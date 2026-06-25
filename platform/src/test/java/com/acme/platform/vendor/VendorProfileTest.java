package com.acme.platform.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Vendor seam profile check: with the default configuration ({@code platform.vendor}
 * unset / mock), the {@link MockVendorClient} is the wired {@link VendorClient}
 * and the {@link SoapVendorClient} stub is absent — so rating / lookup / issuance
 * stay mocked behind the seam.
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
}
