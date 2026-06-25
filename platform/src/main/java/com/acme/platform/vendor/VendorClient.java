package com.acme.platform.vendor;

import java.util.List;
import java.util.Map;

/**
 * The boundary to the external vendor that a real UK motor insurer calls for
 * values it does not own — vehicle data from a registration, and address
 * candidates from a postcode. Services depend only on this interface, never on
 * the transport.
 *
 * <p>Two implementations sit behind this one interface, selected by config
 * ({@code platform.vendor}):
 * <ul>
 *   <li>{@link MockVendorClient} — the <b>default</b> ({@code mock | unset}):
 *       deterministic synthetic data (brief §2/§3/§15).</li>
 *   <li>{@link LiveVendorClient} — the {@code live} variant: today a fail-fast
 *       stub; later the real client calls the vendor kit over whatever transport
 *       it exposes — SOAP, XML-over-HTTP, or REST — decided when the vendor is
 *       integrated.</li>
 * </ul>
 * Both implement this same interface, so swapping mock&rarr;live is
 * <b>config-only</b> ({@code platform.vendor=live}) and changes nothing in the
 * callers ({@code QuoteService} / {@code PricingService} /
 * {@code UnderwritingEngine}).
 *
 * <p><b>Rating</b> ({@link #rate}) belongs here too: in a real insurer the
 * premium is a value obtained from the vendor, not something the platform
 * computes. The platform owns only <i>underwriting</i> (quote / refer /
 * decline); the price itself comes through this seam.
 */
public interface VendorClient {

    /** Resolve a registration to make/model/derivative/fuel/transmission (+ echoed registration). */
    Map<String, Object> lookupVehicle(String registration);

    /** Resolve a postcode to a list of candidate addresses. */
    List<Map<String, Object>> lookupAddress(String postcode);

    /**
     * Rate a quote: return the annual premium plus a transparent breakdown
     * (brief §15). A real insurer obtains this from the vendor, so it
     * lives behind the seam; {@link MockVendorClient#rate} implements the brief's
     * deterministic mock model.
     *
     * @param quoteData the whole-model quote payload (nested maps)
     * @return the rated premium and its {@code {label, amount}} breakdown lines
     */
    RatingResult rate(Map<String, Object> quoteData);

    /**
     * Issue a policy from a priced quote (Slice 8): bind the quote into a
     * policy and return the issued {@code policyNumber}, {@code status}, and
     * {@code effectiveDate}. In a real insurer this is a vendor call
     * (a future {@code LiveVendorClient.issuePolicy(...)} behind this same seam,
     * over SOAP, XML, or REST depending on the vendor kit), so callers depend
     * only on this interface, never on the transport.
     *
     * <p>{@link MockVendorClient#issuePolicy} implements it with a deterministic
     * synthetic policy. <b>Real issuance and payments stay out of scope
     * (brief §2)</b> — only the seam is visible.
     *
     * @param quoteData the whole-model quote payload (nested maps)
     * @return the issued policy: {@code {policyNumber, status, effectiveDate}}
     */
    PolicyResult issuePolicy(Map<String, Object> quoteData);
}
