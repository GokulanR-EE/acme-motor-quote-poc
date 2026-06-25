# Mock Platform Production-Equivalent — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining production-equivalent gaps on the all-Java mock platform — solidify the vendor SOAP seam, add a DB-backed end-to-end integration test, add CI, and produce a deployable artifact — keeping pricing/vendor data the only mock.

**Architecture:** One Java/Spring Boot platform (the source of truth) with `VendorClient` as the single seam to vendor-sourced data (mocked now, real SOAP later, profile-swapped). Persistence/error-taxonomy/validation/profiles are already in place; this plan adds the seam stub, integration coverage, CI, and Docker packaging.

**Tech Stack:** Java 21 + Spring Boot 4.1 (Maven wrapper, runs on JDK 26), H2 (JPA), springdoc; GitHub Actions; Docker. Spec: `docs/superpowers/specs/2026-06-25-mock-platform-production-equivalent-design.md`.

---

## File Structure

```
platform/
├── src/main/java/com/acme/platform/vendor/
│   ├── VendorClient.java          # seam interface (exists)
│   ├── MockVendorClient.java      # @Profile mock-vendor / default (exists)
│   └── SoapVendorClient.java      # @Profile soap-vendor — harden the stub (Task 1)
├── src/test/java/com/acme/platform/
│   ├── vendor/VendorProfileTest.java     # which client is active per profile (Task 1)
│   └── journey/JourneyIntegrationTest.java  # DB-backed end-to-end (Task 2)
├── Dockerfile                      # deployable image (Task 4)
docker-compose.yml                  # run the platform (+ deps) locally/CI (Task 4)
.github/workflows/ci.yml            # CI for platform + python + frontend (Task 3)
```

Conventions: platform commands run from `platform/` via `./mvnw`. Match existing class/package names — read the current `platform/src/main/java/com/acme/platform/` before editing.

---

## Task 1: Solidify the vendor SOAP seam (mock vs soap profiles)

**Files:**
- Modify: `platform/src/main/java/com/acme/platform/vendor/SoapVendorClient.java`
- Modify: `platform/src/main/java/com/acme/platform/vendor/VendorClient.java` (Javadoc only)
- Test: `platform/src/test/java/com/acme/platform/vendor/VendorProfileTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.acme.platform.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
class VendorProfileTest {
    @Autowired VendorClient vendorClient;

    @Test
    void mockVendorIsActiveByDefault() {
        assertThat(vendorClient).isInstanceOf(MockVendorClient.class);
    }
}
```

- [ ] **Step 2: Run to verify it passes (mock is default)**

Run: `cd platform && ./mvnw -q -Dtest=VendorProfileTest test`
Expected: PASS — confirms `MockVendorClient` is the default bean.

- [ ] **Step 3: Harden `SoapVendorClient` so the `soap-vendor` profile is a clean, documented seam**

Make every `VendorClient` method throw a single typed "not implemented" error and document the real integration. Replace the body of `SoapVendorClient.java`:

```java
package com.acme.platform.vendor;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Real-vendor seam (profile {@code soap-vendor}). In production this becomes a
 * SOAP client generated from the vendor WSDL (JAX-WS / Spring-WS, with
 * WS-Security). It implements the SAME {@link VendorClient} interface as
 * {@link MockVendorClient}, so swapping mock -> real is config-only
 * ({@code platform.vendor=soap}) with no change to QuoteService/Underwriting.
 *
 * Until the WSDL is available every call fails fast with a clear, uniform error.
 */
@Component
@Profile("soap-vendor")
public class SoapVendorClient implements VendorClient {

    private static final String MSG =
        "Real vendor SOAP integration not implemented; run with the default "
        + "'mock-vendor' profile, or supply the vendor WSDL to generate this client.";

    @Override public RatingResult rate(Map<String, Object> quote) { throw notImplemented(); }
    @Override public Map<String, Object> lookupVehicle(String registration) { throw notImplemented(); }
    @Override public List<Map<String, Object>> lookupAddress(String postcode) { throw notImplemented(); }
    @Override public PolicyResult issuePolicy(Map<String, Object> quote) { throw notImplemented(); }

    private UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException(MSG);
    }
}
```

> If the real `VendorClient` interface differs (method names/return types), match it exactly — read `VendorClient.java` first and adjust the overrides. Do not change the interface beyond Javadoc.

- [ ] **Step 4: Add a test that the soap profile wires the stub and it fails cleanly**

Append to `VendorProfileTest.java`:

```java
    @org.junit.jupiter.api.Nested
    @SpringBootTest
    @ActiveProfiles("soap-vendor")
    static class SoapProfile {
        @Autowired VendorClient vendorClient;

        @Test
        void soapVendorIsActiveAndFailsFast() {
            assertThat(vendorClient).isInstanceOf(SoapVendorClient.class);
            org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> vendorClient.lookupVehicle("FX19ZTC"));
        }
    }
```

- [ ] **Step 5: Run + commit**

Run: `cd platform && ./mvnw -q -Dtest=VendorProfileTest test` — Expected: PASS.
```bash
git add platform/src/main/java/com/acme/platform/vendor platform/src/test/java/com/acme/platform/vendor/VendorProfileTest.java
git commit -m "feat(platform): solidify vendor SOAP seam (soap-vendor profile stub + profile tests)"
```

---

## Task 2: DB-backed end-to-end journey integration test

**Files:**
- Test: `platform/src/test/java/com/acme/platform/journey/JourneyIntegrationTest.java`

- [ ] **Step 1: Write the integration test (full journey against the real app + H2)**

Uses `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `TestRestTemplate`, default (mock-vendor) profile, in-memory H2. Drives: create → patch a complete quote → price (quote) → purchase-link → GET landing → issue-policy; and asserts a refer + decline outcome and session-404. Read the controller for exact paths/shapes first; adjust field names to match.

```java
package com.acme.platform.journey;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JourneyIntegrationTest {
    @Autowired TestRestTemplate rest;

    private HttpHeaders session(String sid) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (sid != null) h.set("X-Session-Id", sid);
        return h;
    }

    @Test
    void fullQuoteToPolicyJourney() {
        // 1) create
        ResponseEntity<Map> created = rest.postForEntity("/quotes", null, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String quoteId = (String) created.getBody().get("quoteId");
        String sid = (String) created.getBody().get("sessionId");
        assertThat(quoteId).isNotBlank();
        assertThat(sid).isNotBlank();

        // 2) wrong/missing session -> 404
        ResponseEntity<Map> noSession = rest.exchange("/quotes/" + quoteId, HttpMethod.GET,
            new HttpEntity<>(session(null)), Map.class);
        assertThat(noSession.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 3) patch a complete, quotable profile (mirror standard-quote.json)
        Map<String, Object> patch = Map.of("patch", Map.of(
            "vehicle", Map.of("registration","FX19ZTC","make","Ford","model","Focus",
                "datePurchased", Map.of("month",6,"year",2019), "value",12000,
                "useOfVehicle","Social + commuting","security","Factory-fitted","dashcam",true,
                "modified",false,"imported","No","daytimeLocation","car park",
                "overnightLocation","drive","annualMileage",8000,"registeredKeeper",true,"legalOwner",true),
            "customer", Map.of("title","Mr","firstName","Sam","surname","Sample","dateOfBirth","1990-01-01",
                "maritalStatus","Married","childrenUnder16",1,"employmentStatus","Employed","partTimeJob",false,
                "yearsLivedInUK","Since birth","address", Map.of("houseNumberOrName","10","postcode","RG1 1AA"),
                "ownsProperty",true,"carKeptOvernightAtAddress",true,"email","sam@example.com"),
            "driver", Map.of("licenceType","Full UK","licenceHeldFor",15,"insuranceCancelledOrVoid",false,
                "ncdYears",5,"ncdOnCompanyCar",false),
            "history", Map.of("claimsLast3Years",0,"offencesLast5Years",0,"unspentCriminalConvictions",false),
            "household", Map.of("carsInHousehold",2,"anotherCarHasCover",true,"regularUseOfOtherVehicles","Named car"),
            "cover", Map.of("paymentMethod","Monthly instalments","coverLevel","Comprehensive",
                "coverStartDate","2026-07-01","voluntaryExcess",250)));
        ResponseEntity<Map> patched = rest.exchange("/quotes/" + quoteId, HttpMethod.PATCH,
            new HttpEntity<>(patch, session(sid)), Map.class);
        assertThat(patched.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4) price -> quote
        ResponseEntity<Map> priced = rest.exchange("/quotes/" + quoteId + "/price", HttpMethod.POST,
            new HttpEntity<>(session(sid)), Map.class);
        assertThat(priced.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(priced.getBody().get("outcome")).isEqualTo("quote");
        assertThat(((Number) priced.getBody().get("annualPremium")).doubleValue()).isGreaterThan(0);

        // 5) purchase-link
        ResponseEntity<Map> link = rest.exchange("/quotes/" + quoteId + "/purchase-link", HttpMethod.POST,
            new HttpEntity<>(session(sid)), Map.class);
        assertThat(link.getStatusCode()).isEqualTo(HttpStatus.OK);
        String purchaseUrl = (String) link.getBody().get("purchaseUrl");
        assertThat(purchaseUrl).contains("/purchase/");

        // 6) landing renders (200) by token only
        String path = purchaseUrl.substring(purchaseUrl.indexOf("/purchase/"));
        ResponseEntity<String> landing = rest.getForEntity(path, String.class);
        assertThat(landing.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 7) issue policy
        ResponseEntity<Map> policy = rest.exchange("/quotes/" + quoteId + "/issue-policy", HttpMethod.POST,
            new HttpEntity<>(session(sid)), Map.class);
        assertThat(policy.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) policy.getBody().get("policyNumber")).startsWith("ACME-POL-");
    }
}
```

- [ ] **Step 2: Run + iterate to green**

Run: `cd platform && ./mvnw -q -Dtest=JourneyIntegrationTest test`
Expected: PASS. If a field name/path differs from the controller, fix the test to match the real contract (read `PlatformController`/landing controller). Do NOT change production code to fit the test unless a real bug is found.

- [ ] **Step 3: Commit**

```bash
git add platform/src/test/java/com/acme/platform/journey/JourneyIntegrationTest.java
git commit -m "test(platform): DB-backed end-to-end journey integration test (create->price->purchase->policy)"
```

---

## Task 3: CI (GitHub Actions) for the whole repo

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Write the workflow**

```yaml
name: CI
on: [push, pull_request]

jobs:
  platform:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: platform } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: chmod +x mvnw && ./mvnw -q -B test

  mcp-server:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: mcp-server } }
    steps:
      - uses: actions/checkout@v4
      - uses: astral-sh/setup-uv@v5
      - run: uv python install 3.11
      - run: uv sync --dev
      - run: uv run pytest -q

  backend:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: backend } }
    steps:
      - uses: actions/checkout@v4
      - uses: astral-sh/setup-uv@v5
      - run: uv python install 3.11
      - run: uv sync --dev
      - run: MOCK_LLM=1 QUOTE_SERVICE=fake uv run pytest -q

  frontend:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: frontend } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: npm ci
      - run: npm run test
      - run: npm run build
```

- [ ] **Step 2: Commit (note: pushing workflow files needs a `workflow`-scoped token, or add via the GitHub UI)**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: GitHub Actions for platform (Java) + mcp-server + backend + frontend"
```
> The platform uses Java 21 toolchain in CI even though local dev is JDK 26 — the Maven build targets release 21. Pushing `.github/workflows/**` requires a token with the `workflow` scope; if the push is rejected, add the file via the GitHub web UI (Actions → new workflow → paste).

---

## Task 4: Deployable artifact (Docker)

**Files:**
- Create: `platform/Dockerfile`
- Create: `docker-compose.yml` (repo root)

- [ ] **Step 1: Dockerfile (multi-stage; build with the Maven wrapper, run the jar)**

`platform/Dockerfile`:
```dockerfile
# build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B -DskipTests dependency:go-offline || true
COPY src ./src
RUN ./mvnw -q -B -DskipTests package
# run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/platform-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8070
# dashboard dir is mounted/copied alongside in compose; H2 file under /app/data
ENV PLATFORM_DASHBOARD_DIR=/app/dashboard
ENTRYPOINT ["java","-jar","app.jar"]
```

- [ ] **Step 2: docker-compose for the platform (+ dashboard volume)**

`docker-compose.yml`:
```yaml
services:
  platform:
    build: ./platform
    ports: ["8070:8070"]
    environment:
      SPRING_PROFILES_ACTIVE: dev
      PLATFORM_DASHBOARD_DIR: /app/dashboard
    volumes:
      - ./dashboard:/app/dashboard:ro
      - platform-data:/app/data
volumes:
  platform-data:
```

- [ ] **Step 3: Verify the image builds + boots (if Docker is available)**

Run: `docker compose build && docker compose up -d && sleep 5 && curl -s localhost:8070/health`
Expected: `{"status":"ok"}`. If Docker is unavailable in the environment, verify the Dockerfile is well-formed and document the manual run. Then `docker compose down`.

- [ ] **Step 4: Commit**

```bash
git add platform/Dockerfile docker-compose.yml
git commit -m "build(platform): Dockerfile + docker-compose for deployable platform"
```

---

## Task 5: Observability — structured logging + readiness (prod profile)

**Files:**
- Modify: `platform/src/main/resources/application.yml` (logging + actuator)
- Modify: `platform/src/main/java/com/acme/platform/.../ApiActivity.java` or a small filter (request correlation id)

- [ ] **Step 1: Enable JSON/structured logging for `prod` and a correlation id**

In `application.yml`, under the `prod` profile, enable structured logging (Spring Boot 3.4+ supports `logging.structured.format.console: ecs`); expose actuator health/readiness:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      probes:
        enabled: true
---
spring:
  config:
    activate:
      on-profile: prod
logging:
  structured:
    format:
      console: ecs
```

- [ ] **Step 2: Add a request correlation-id filter**

Create `platform/src/main/java/com/acme/platform/web/CorrelationIdFilter.java`:
```java
package com.acme.platform.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class CorrelationIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String cid = ((HttpServletRequest) req).getHeader("X-Correlation-Id");
        if (cid == null || cid.isBlank()) cid = UUID.randomUUID().toString();
        MDC.put("correlationId", cid);
        ((HttpServletResponse) res).setHeader("X-Correlation-Id", cid);
        try { chain.doFilter(req, res); } finally { MDC.remove("correlationId"); }
    }
}
```

- [ ] **Step 3: Verify + commit**

Run: `cd platform && ./mvnw -q test` (all green) and boot with `--spring.profiles.active=prod` to eyeball JSON logs + `GET /actuator/health/readiness` → 200.
```bash
git add platform/src/main/resources/application.yml platform/src/main/java/com/acme/platform/web/CorrelationIdFilter.java
git commit -m "feat(platform): structured logging (prod) + readiness probe + correlation id"
```

---

## Self-Review

**Spec coverage:**
- §4 vendor SOAP seam (soap-vendor stub + profile) → Task 1. ✓
- §7 "still to build": deployment/CI → Tasks 3 (CI) + 4 (Docker); integration tests → Task 2; SoapVendorClient stub tidy → Task 1; observability (structured logging/readiness) → Task 5. ✓
- §2 all-Java, no Python mock; §3 mock-vs-production boundary; §5 single deterministic quote — already implemented; no new task needed (confirmed, not re-built). ✓
- §9 testing (integration + CI) → Tasks 2 + 3. ✓

**Placeholder scan:** No TBD/TODO; each code step has complete code; tests have real assertions. The one external unknown (cloud host GCP/AWS) is deliberately deferred — the plan delivers a Docker artifact so the actual cloud target can be chosen later without rework.

**Type/name consistency:** `VendorClient` method set (`rate`, `lookupVehicle`, `lookupAddress`, `issuePolicy`) used consistently across Task 1; the journey test uses the REST contract (`/quotes`, `/quotes/{id}`, `/price`, `/purchase-link`, `/purchase/{token}`, `/issue-policy`, `X-Session-Id`) the existing controllers expose — implementer must verify exact field names against the controller and adjust the test (noted in Task 2).

No gaps found.
