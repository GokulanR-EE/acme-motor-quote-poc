package com.acme.platform.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.acme.platform.vendor.RatingResult;
import com.acme.platform.vendor.VendorClient;

/**
 * Unit tests for {@link PricingService} with a mocked {@link VendorClient} (so
 * the rated premium/breakdown are known inputs) and a real
 * {@link UnderwritingEngine} (so the quote/refer/decline decision is driven by
 * real quote inputs). Pins down: vendor echo of premium + breakdown, the
 * 10-instalment split, the compulsory + voluntary excess sum, and outcome/reasons.
 */
@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private VendorClient vendor;

    private PricingService service;

    @BeforeEach
    void setUp() {
        service = new PricingService(vendor, new UnderwritingEngine());
    }

    private void stubRating(double premium, List<Map<String, Object>> breakdown) {
        when(vendor.rate(org.mockito.ArgumentMatchers.anyMap()))
            .thenReturn(new RatingResult(premium, breakdown));
    }

    private static List<Map<String, Object>> breakdown(double base, double extra) {
        return List.of(
            Map.of("label", "Base premium", "amount", base),
            Map.of("label", "Other", "amount", extra));
    }

    /** A clean, quotable whole-model payload (adult driver, supported vehicle, no refer triggers). */
    private static Map<String, Object> cleanQuote() {
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("dateOfBirth", LocalDate.now().minusYears(40).toString());

        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("value", 12_000);
        vehicle.put("supported", true);

        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("ncdYears", 5);

        Map<String, Object> cover = new LinkedHashMap<>();
        cover.put("voluntaryExcess", 250);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customer", customer);
        data.put("vehicle", vehicle);
        data.put("driver", driver);
        data.put("cover", cover);
        return data;
    }

    @Test
    void echoesVendorPremiumCurrencyAndBreakdown() {
        List<Map<String, Object>> bd = breakdown(600.00, 100.00);
        stubRating(700.00, bd);

        Map<String, Object> pricing = service.price(cleanQuote());

        assertThat(pricing.get("annualPremium")).isEqualTo(700.00);
        assertThat(pricing.get("currency")).isEqualTo("GBP");
        assertThat(pricing.get("iptIncluded")).isEqualTo(true);
        assertThat(pricing.get("breakdown")).isEqualTo(bd);
    }

    @Test
    void breakdownLinesStillSumToTheEchoedPremium() {
        stubRating(700.00, breakdown(600.00, 100.00));

        Map<String, Object> pricing = service.price(cleanQuote());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bd = (List<Map<String, Object>>) pricing.get("breakdown");
        double sum = bd.stream()
            .mapToDouble(line -> ((Number) line.get("amount")).doubleValue())
            .sum();

        assertThat(sum).isEqualTo((double) (Double) pricing.get("annualPremium"));
    }

    @Test
    void splitsAnnualPremiumIntoDepositPlusTenEqualInstalments() {
        stubRating(1234.55, breakdown(1234.55, 0.00));

        Map<String, Object> pricing = service.price(cleanQuote());

        @SuppressWarnings("unchecked")
        Map<String, Object> monthly = (Map<String, Object>) pricing.get("monthly");

        assertThat(monthly.get("instalments")).isEqualTo(10);

        double deposit = (double) monthly.get("deposit");
        double instalment = (double) monthly.get("instalment");

        // 1234.55 / 10 = 123.455 -> HALF_UP 123.46 per instalment.
        assertThat(instalment).isEqualTo(123.46);
        // deposit = total - instalment * 9 = 1234.55 - 1111.14 = 123.41
        assertThat(deposit).isEqualTo(123.41);
        // Invariant: deposit + instalment * 9 == annual premium.
        assertThat(deposit + instalment * 9).isCloseTo(1234.55, within(1e-9));
    }

    @Test
    void compulsoryExcessIsAddedToVoluntaryExcess() {
        stubRating(500.00, breakdown(500.00, 0.00));

        Map<String, Object> data = cleanQuote();
        @SuppressWarnings("unchecked")
        Map<String, Object> cover = (Map<String, Object>) data.get("cover");
        cover.put("voluntaryExcess", 250);

        Map<String, Object> pricing = service.price(data);

        assertThat(pricing.get("compulsoryExcess")).isEqualTo(PricingService.COMPULSORY_EXCESS);
        assertThat(pricing.get("voluntaryExcess")).isEqualTo(250);
        assertThat(pricing.get("totalExcess")).isEqualTo(PricingService.COMPULSORY_EXCESS + 250);
        // Sanity: compulsory excess constant is the brief's 350.
        assertThat(pricing.get("totalExcess")).isEqualTo(600);
    }

    @Test
    void missingVoluntaryExcessDefaultsToZeroSoTotalEqualsCompulsory() {
        stubRating(500.00, breakdown(500.00, 0.00));

        Map<String, Object> data = cleanQuote();
        @SuppressWarnings("unchecked")
        Map<String, Object> cover = (Map<String, Object>) data.get("cover");
        cover.remove("voluntaryExcess");

        Map<String, Object> pricing = service.price(data);

        assertThat(pricing.get("voluntaryExcess")).isEqualTo(0);
        assertThat(pricing.get("totalExcess")).isEqualTo(PricingService.COMPULSORY_EXCESS);
    }

    @Test
    void echoesNcdYearsFromDriverSection() {
        stubRating(500.00, breakdown(500.00, 0.00));

        Map<String, Object> pricing = service.price(cleanQuote());

        assertThat(pricing.get("ncdYears")).isEqualTo(5);
    }

    @Test
    void cleanQuoteYieldsQuoteOutcomeWithNoReasons() {
        stubRating(500.00, breakdown(500.00, 0.00));

        Map<String, Object> pricing = service.price(cleanQuote());

        assertThat(pricing.get("outcome")).isEqualTo(UnderwritingOutcome.QUOTE);
        assertThat((List<?>) pricing.get("reasons")).isEmpty();
    }

    @Test
    void highValueVehicleYieldsReferOutcomeWithReason() {
        stubRating(500.00, breakdown(500.00, 0.00));

        Map<String, Object> data = cleanQuote();
        @SuppressWarnings("unchecked")
        Map<String, Object> vehicle = (Map<String, Object>) data.get("vehicle");
        vehicle.put("value", 80_000); // > £75,000 refer threshold

        Map<String, Object> pricing = service.price(data);

        assertThat(pricing.get("outcome")).isEqualTo(UnderwritingOutcome.REFER);
        @SuppressWarnings("unchecked")
        List<String> referReasons = (List<String>) pricing.get("reasons");
        assertThat(referReasons).contains("Vehicle value exceeds £75,000");
    }

    @Test
    void underageDriverYieldsDeclineOutcomeWithReason() {
        stubRating(500.00, breakdown(500.00, 0.00));

        Map<String, Object> data = cleanQuote();
        @SuppressWarnings("unchecked")
        Map<String, Object> customer = (Map<String, Object>) data.get("customer");
        customer.put("dateOfBirth", LocalDate.now().minusYears(16).toString());

        Map<String, Object> pricing = service.price(data);

        assertThat(pricing.get("outcome")).isEqualTo(UnderwritingOutcome.DECLINE);
        @SuppressWarnings("unchecked")
        List<String> declineReasons = (List<String>) pricing.get("reasons");
        assertThat(declineReasons).contains("Driver is under 18");
    }

    @Test
    void journeyStateMapsOutcomesToJourneyStates() {
        assertThat(PricingService.journeyStateFor(UnderwritingOutcome.QUOTE)).isEqualTo("quoted");
        assertThat(PricingService.journeyStateFor(UnderwritingOutcome.REFER)).isEqualTo("referred");
        assertThat(PricingService.journeyStateFor(UnderwritingOutcome.DECLINE)).isEqualTo("declined");
        assertThat(PricingService.journeyStateFor("anything-else")).isEqualTo("ready_to_price");
    }

    @Test
    void premiumIsRoundedToTwoDecimalPlaces() {
        stubRating(499.999, breakdown(499.999, 0.00));

        Map<String, Object> pricing = service.price(cleanQuote());

        assertThat(pricing.get("annualPremium")).isEqualTo(500.00);
    }
}
