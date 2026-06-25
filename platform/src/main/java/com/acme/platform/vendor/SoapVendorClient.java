package com.acme.platform.vendor;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real-vendor seam (the {@code soap-vendor} variant, {@code platform.vendor=soap}).
 * The default {@code mock-vendor} seam ({@link MockVendorClient}) is active
 * otherwise.
 *
 * <p>In production this becomes a SOAP client <b>generated from the vendor
 * WSDL</b> (JAX-WS / Spring-WS, with WS-Security for the message-level
 * credentials a real insurer's vendor requires). It implements the <b>same</b>
 * {@link VendorClient} interface as {@link MockVendorClient}, so swapping
 * mock&rarr;SOAP is <b>config-only</b> ({@code platform.vendor=soap}) — no change
 * to {@code QuoteService}, {@code PricingService}, or {@code UnderwritingEngine}.
 *
 * <p>Until the WSDL is available, every method <b>fails fast</b> with one clear,
 * uniform {@link UnsupportedOperationException} — this bean documents the
 * production seam rather than implementing it. Rating, vehicle/address lookup,
 * and policy issuance all remain mocked behind {@link MockVendorClient} for the
 * PoC (brief §2, §3, §15).
 */
@Component
@ConditionalOnProperty(name = "platform.vendor", havingValue = "soap")
public class SoapVendorClient implements VendorClient {

    /** Single, clear message for every unimplemented seam method. */
    static final String NOT_IMPLEMENTED =
        "real vendor SOAP integration not implemented; use the default mock-vendor "
        + "profile or supply the vendor WSDL";

    @Override
    public Map<String, Object> lookupVehicle(String registration) {
        throw notImplemented();
    }

    @Override
    public List<Map<String, Object>> lookupAddress(String postcode) {
        throw notImplemented();
    }

    @Override
    public RatingResult rate(Map<String, Object> quoteData) {
        throw notImplemented();
    }

    @Override
    public PolicyResult issuePolicy(Map<String, Object> quoteData) {
        throw notImplemented();
    }

    private static UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
