package com.acme.platform.vendor;

import java.util.Map;

/**
 * Seam to the external vendor that a real insurer calls over SOAP for the values
 * it does not own (vehicle lookup, rating, etc.).
 *
 * <p>For Slice 1 this exposes a single representative call, {@link #systemInfo()}.
 *
 * <p><b>Real implementation:</b> a {@code SoapVendorClient} (JAX-WS / Spring-WS
 * stubs generated from the vendor's WSDL) will drop into this interface with no
 * change to the rest of the platform. {@code QuoteService}, {@code Rating} and
 * {@code Underwriting} will depend on this interface in later slices, so swapping
 * the mock for the real SOAP client is a one-line wiring change.
 */
public interface VendorClient {

    /**
     * Represents a vendor call. Returns a synthetic system-info map in the mock.
     *
     * @return vendor system information
     */
    Map<String, Object> systemInfo();
}
