package com.acme.platform.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * DB-backed end-to-end journey test against the real running app (random port,
 * default mock-vendor profile, in-memory H2). Drives the full REST contract the
 * MCP / conversation layer depends on:
 *
 * <pre>
 *   create → (missing session → 404) → patch a complete quotable profile
 *          → /price (outcome quote, premium &gt; 0, breakdown sums) → /purchase-link
 *          → GET landing (200) → /issue-policy (ACME-POL-…)
 * </pre>
 *
 * plus a <b>refer</b> (high-value vehicle) and a <b>decline</b> (under-18 driver)
 * outcome. Assertions match the live controller shapes; production code is not
 * changed to fit the test. Uses Spring's {@link RestClient} against the bound
 * port so no extra test-client dependency is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JourneyIntegrationTest {

    @LocalServerPort int port;

    private RestClient rest;

    @BeforeEach
    void setUp() {
        // Don't throw on 4xx/5xx — the journey asserts on status codes itself.
        rest = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(s -> true, (req, res) -> { })
            .build();
    }

    // ---------------------------------------------------------------------
    // Happy path: create → price (quote) → purchase-link → landing → policy
    // ---------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void fullQuoteToPolicyJourney() {
        // 1) create
        var created = post("/quotes", null, null);
        assertThat(created.status).isEqualTo(201);
        Map<String, Object> createdBody = created.body;
        String quoteId = (String) createdBody.get("quoteId");
        String sid = (String) createdBody.get("sessionId");
        assertThat(quoteId).isNotBlank();
        assertThat(sid).isNotBlank();

        // 2) missing session -> 404 (existence is never revealed)
        var noSession = get("/quotes/" + quoteId, null);
        assertThat(noSession.status).isEqualTo(404);

        // 3) patch a complete, quotable profile
        var patched = patch("/quotes/" + quoteId, sid, Map.of("patch", completeQuotablePatch()));
        assertThat(patched.status).isEqualTo(200);
        assertThat((List<String>) patched.body.get("missingFields")).isEmpty();

        // 4) price -> quote, premium > 0, breakdown sums to premium
        var priced = post("/quotes/" + quoteId + "/price", sid, null);
        assertThat(priced.status).isEqualTo(200);
        assertThat(priced.body.get("outcome")).isEqualTo("quote");
        double premium = ((Number) priced.body.get("annualPremium")).doubleValue();
        assertThat(premium).isGreaterThan(0);
        double sum = ((List<Map<String, Object>>) priced.body.get("breakdown")).stream()
            .mapToDouble(l -> ((Number) l.get("amount")).doubleValue())
            .sum();
        assertThat(sum).isCloseTo(premium, within(0.001));

        // 5) purchase-link
        var link = post("/quotes/" + quoteId + "/purchase-link", sid, null);
        assertThat(link.status).isEqualTo(200);
        String purchaseUrl = (String) link.body.get("purchaseUrl");
        assertThat(purchaseUrl).contains("/purchase/");

        // 6) landing renders (200) by token only — no session
        String path = purchaseUrl.substring(purchaseUrl.indexOf("/purchase/"));
        String html = rest.get().uri(path).retrieve().body(String.class);
        assertThat(html).contains("Annual premium");

        // 7) issue policy
        var policy = post("/quotes/" + quoteId + "/issue-policy", sid, null);
        assertThat(policy.status).isEqualTo(200);
        assertThat((String) policy.body.get("policyNumber")).startsWith("ACME-POL-");
        assertThat(policy.body.get("status")).isEqualTo("ISSUED");

        // 8) GET reflects the completed journey
        var finalState = get("/quotes/" + quoteId, sid);
        assertThat(finalState.body.get("journeyState")).isEqualTo("policy_issued");
    }

    // ---------------------------------------------------------------------
    // Refer: a high-value vehicle (> £75k) refers to a human.
    // ---------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void highValueVehicleRefers() {
        String[] q = newQuote();
        Map<String, Object> patch = completeQuotablePatch();
        ((Map<String, Object>) patch.get("vehicle")).put("value", 90_000);
        assertThat(patch("/quotes/" + q[0], q[1], Map.of("patch", patch)).status).isEqualTo(200);

        var priced = post("/quotes/" + q[0] + "/price", q[1], null);
        assertThat(priced.status).isEqualTo(200);
        assertThat(priced.body.get("outcome")).isEqualTo("refer");

        // A refer is not purchasable -> 409.
        var link = post("/quotes/" + q[0] + "/purchase-link", q[1], null);
        assertThat(link.status).isEqualTo(409);
    }

    // ---------------------------------------------------------------------
    // Decline: an under-18 driver is declined.
    // ---------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void underageDriverIsDeclined() {
        String[] q = newQuote();
        Map<String, Object> patch = completeQuotablePatch();
        ((Map<String, Object>) patch.get("customer")).put("dateOfBirth",
            java.time.LocalDate.now().minusYears(16).toString());
        assertThat(patch("/quotes/" + q[0], q[1], Map.of("patch", patch)).status).isEqualTo(200);

        var priced = post("/quotes/" + q[0] + "/price", q[1], null);
        assertThat(priced.status).isEqualTo(200);
        assertThat(priced.body.get("outcome")).isEqualTo("decline");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** A response: HTTP status + parsed JSON body. */
    private record Resp(int status, Map<String, Object> body) {
    }

    @SuppressWarnings("unchecked")
    private Resp get(String path, String sid) {
        var spec = rest.get().uri(path);
        if (sid != null) {
            spec = spec.header("X-Session-Id", sid);
        }
        var res = spec.retrieve().toEntity(Map.class);
        return new Resp(res.getStatusCode().value(), res.getBody());
    }

    @SuppressWarnings("unchecked")
    private Resp post(String path, String sid, Object body) {
        var spec = rest.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (sid != null) {
            spec = spec.header("X-Session-Id", sid);
        }
        var res = (body == null ? spec : spec.body(body)).retrieve().toEntity(Map.class);
        return new Resp(res.getStatusCode().value(), res.getBody());
    }

    @SuppressWarnings("unchecked")
    private Resp patch(String path, String sid, Object body) {
        var res = rest.patch().uri(path).contentType(MediaType.APPLICATION_JSON)
            .header("X-Session-Id", sid).body(body).retrieve().toEntity(Map.class);
        return new Resp(res.getStatusCode().value(), res.getBody());
    }

    private String[] newQuote() {
        var created = post("/quotes", null, null);
        assertThat(created.status).isEqualTo(201);
        return new String[]{(String) created.body.get("quoteId"),
            (String) created.body.get("sessionId")};
    }

    /** A complete, low-risk quotable profile — every mandatory field present. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> completeQuotablePatch() {
        Map<String, Object> patch = new LinkedHashMap<>();

        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("registration", "FX19ZTC");
        vehicle.put("make", "Ford");
        vehicle.put("model", "Focus");
        vehicle.put("datePurchased", Map.of("month", 6, "year", 2019));
        vehicle.put("value", 12_000);
        vehicle.put("useOfVehicle", "Social + commuting");
        vehicle.put("security", "Factory-fitted");
        vehicle.put("dashcam", true);
        vehicle.put("modified", false);
        vehicle.put("imported", "No");
        vehicle.put("daytimeLocation", "car park");
        vehicle.put("overnightLocation", "drive");
        vehicle.put("annualMileage", 8_000);
        vehicle.put("registeredKeeper", true);
        vehicle.put("legalOwner", true);
        patch.put("vehicle", vehicle);

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("title", "Mr");
        customer.put("firstName", "Sam");
        customer.put("surname", "Sample");
        customer.put("dateOfBirth", "1990-01-01");
        customer.put("maritalStatus", "Married");
        customer.put("childrenUnder16", 1);
        customer.put("employmentStatus", "Employed");
        customer.put("partTimeJob", false);
        customer.put("yearsLivedInUK", "Since birth");
        customer.put("address", Map.of("houseNumberOrName", "10", "postcode", "RG1 1AA"));
        customer.put("ownsProperty", true);
        customer.put("carKeptOvernightAtAddress", true);
        customer.put("email", "sam@example.com");
        patch.put("customer", customer);

        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("licenceType", "Full UK");
        driver.put("licenceHeldFor", 15);
        driver.put("insuranceCancelledOrVoid", false);
        driver.put("ncdYears", 5);
        driver.put("ncdOnCompanyCar", false);
        patch.put("driver", driver);

        Map<String, Object> history = new LinkedHashMap<>();
        history.put("claimsLast3Years", 0);
        history.put("offencesLast5Years", 0);
        history.put("unspentCriminalConvictions", false);
        patch.put("history", history);

        Map<String, Object> household = new LinkedHashMap<>();
        household.put("carsInHousehold", 2);
        household.put("anotherCarHasCover", true);
        household.put("regularUseOfOtherVehicles", "Named car");
        patch.put("household", household);

        Map<String, Object> cover = new LinkedHashMap<>();
        cover.put("paymentMethod", "Monthly instalments");
        cover.put("coverLevel", "Comprehensive");
        cover.put("coverStartDate", "2026-07-01");
        cover.put("voluntaryExcess", 250);
        patch.put("cover", cover);

        return patch;
    }
}
