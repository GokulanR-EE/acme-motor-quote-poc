package com.acme.platform.vendor;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production seam stub for the real vendor integration over SOAP. Activated by
 * the {@code soap-vendor} profile ({@code platform.vendor=soap}); the default
 * {@code mock-vendor} seam ({@link MockVendorClient}) is active otherwise.
 *
 * <p>Every method throws {@link UnsupportedOperationException} — this bean exists
 * to <b>document the production seam</b> (where a real insurer would call the
 * vendor's WSDL-generated stubs with WS-Security), not to implement it. Rating,
 * vehicle/address lookup, and policy issuance all remain mocked behind
 * {@link MockVendorClient} for the PoC (brief §2, §3, §15).
 */
@Component
@ConditionalOnProperty(name = "platform.vendor", havingValue = "soap")
public class SoapVendorClient implements VendorClient {

    private static final String NOT_IMPLEMENTED = "real SOAP integration not implemented";

    @Override
    public Map<String, Object> lookupVehicle(String registration) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<Map<String, Object>> lookupAddress(String postcode) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public RatingResult rate(Map<String, Object> quoteData) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public PolicyResult issuePolicy(Map<String, Object> quoteData) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
