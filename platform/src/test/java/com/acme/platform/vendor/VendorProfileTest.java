package com.acme.platform.vendor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Vendor seam profile check. The seam is a single interface ({@link VendorClient})
 * with two config-selected implementations:
 * <ul>
 *   <li>default ({@code platform.vendor} unset / mock) → {@link MockVendorClient}
 *       is wired and the {@link LiveVendorClient} stub is absent;</li>
 *   <li>{@code platform.vendor=live} → {@link LiveVendorClient} is wired and
 *       every call fails fast.</li>
 * </ul>
 * This proves the mock&rarr;live swap is config-only, with no other code change.
 * The live client's wire protocol (SOAP, XML, or REST) is an implementation
 * detail decided when the vendor is integrated.
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
    void liveVendorStubIsNotWiredByDefault() {
        assertThat(context.getBeanNamesForType(LiveVendorClient.class)).isEmpty();
    }

    /**
     * With {@code platform.vendor=live} the seam swaps to the
     * {@link LiveVendorClient} stub, and every method fails fast with one clear
     * {@link UnsupportedOperationException} until the vendor is integrated.
     */
    @Nested
    @SpringBootTest(properties = "platform.vendor=live")
    class LiveProfile {

        @Autowired VendorClient liveVendorClient;

        @Test
        void liveVendorIsActiveAndEveryMethodFailsFast() {
            assertThat(liveVendorClient).isInstanceOf(LiveVendorClient.class);

            assertThatThrownBy(() -> liveVendorClient.lookupVehicle("FX19ZTC"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not implemented");
            assertThatThrownBy(() -> liveVendorClient.lookupAddress("RG1 1AA"))
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> liveVendorClient.rate(java.util.Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> liveVendorClient.issuePolicy(java.util.Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
