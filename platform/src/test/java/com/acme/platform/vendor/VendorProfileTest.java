package com.acme.platform.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Vendor seam profile check. The seam is a single interface ({@link VendorClient})
 * with two config-selected implementations:
 * <ul>
 *   <li>default ({@code platform.vendor} unset / mock) → {@link MockVendorClient}
 *       is wired and the {@link SoapVendorClient} stub is absent;</li>
 *   <li>{@code soap-vendor} profile ({@code platform.vendor=soap}) →
 *       {@link SoapVendorClient} is wired and every call fails fast.</li>
 * </ul>
 * This proves the mock&rarr;SOAP swap is config-only, with no other code change.
 */
@SpringBootTest
class VendorProfileTest {

    @Autowired VendorClient vendorClient;
    @Autowired org.springframework.context.ApplicationContext context;

    @Test
    void mockVendorIsActiveByDefault() {
        assertThat(vendorClient).isInstanceOf(MockVendorClient.class);
    }

    @Test
    void soapVendorStubIsNotWiredByDefault() {
        assertThat(context.getBeanNamesForType(SoapVendorClient.class)).isEmpty();
    }

    /**
     * Under the {@code soap-vendor} profile the seam swaps to the
     * {@link SoapVendorClient} stub, and every method fails fast with one clear
     * {@link UnsupportedOperationException} until the vendor WSDL is supplied.
     */
    @Nested
    @SpringBootTest
    @ActiveProfiles("soap-vendor")
    class SoapProfile {

        @Autowired VendorClient soapVendorClient;

        @Test
        void soapVendorIsActiveAndEveryMethodFailsFast() {
            assertThat(soapVendorClient).isInstanceOf(SoapVendorClient.class);

            assertThatThrownBy(() -> soapVendorClient.lookupVehicle("FX19ZTC"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not implemented");
            assertThatThrownBy(() -> soapVendorClient.lookupAddress("RG1 1AA"))
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> soapVendorClient.rate(java.util.Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> soapVendorClient.issuePolicy(java.util.Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
