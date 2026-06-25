package com.acme.platform.vendor;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The live-vendor seam (the {@code live} variant, {@code platform.vendor=live}).
 * The default mock-vendor seam ({@link MockVendorClient}) is active otherwise.
 *
 * <p>This is the boundary to the external vendor that supplies rating/pricing and
 * vehicle/address data. The real implementation calls the vendor kit over whatever
 * transport it exposes — SOAP, XML-over-HTTP, or REST — decided when the vendor is
 * integrated. It implements the <b>same</b> {@link VendorClient} interface as
 * {@link MockVendorClient}, so swapping mock&rarr;live is <b>config-only</b>
 * ({@code platform.vendor=live}) — no change to {@code QuoteService},
 * {@code PricingService}, or {@code UnderwritingEngine}.
 *
 * <p>Until the vendor is integrated, every method <b>fails fast</b> with one clear,
 * uniform {@link UnsupportedOperationException} — this bean documents the
 * production seam rather than implementing it. Rating, vehicle/address lookup,
 * and policy issuance all remain mocked behind {@link MockVendorClient} for the
 * PoC (brief §2, §3, §15).
 */
@Component
@ConditionalOnProperty(name = "platform.vendor", havingValue = "live")
public class LiveVendorClient implements VendorClient {

    /** Single, clear message for every unimplemented seam method. */
    static final String NOT_IMPLEMENTED =
        "live vendor integration not implemented; using the default mock vendor";

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
