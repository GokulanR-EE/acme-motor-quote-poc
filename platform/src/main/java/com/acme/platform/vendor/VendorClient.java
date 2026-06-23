package com.acme.platform.vendor;

import java.util.List;
import java.util.Map;

/**
 * The external-vendor seam a real UK motor insurer calls <b>over SOAP</b> for
 * values it does not own — vehicle data from a registration, and address
 * candidates from a postcode. Services depend only on this interface, never on
 * the transport.
 *
 * <p>Today {@link MockVendorClient} implements it with deterministic synthetic
 * data. Later, a {@code SoapVendorClient} — generated from the vendor WSDL with
 * JAX-WS / Spring-WS stubs and WS-Security as needed — will implement this same
 * interface, so swapping mock&rarr;SOAP changes nothing in the callers.
 */
public interface VendorClient {

    /** Resolve a registration to make/model/derivative/fuel/transmission (+ echoed registration). */
    Map<String, Object> lookupVehicle(String registration);

    /** Resolve a postcode to a list of candidate addresses. */
    List<Map<String, Object>> lookupAddress(String postcode);
}
