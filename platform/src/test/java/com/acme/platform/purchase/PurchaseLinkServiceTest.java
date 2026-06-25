package com.acme.platform.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.acme.platform.persistence.PurchaseTokenRepository;

/**
 * Repository-backed purchase-link tests: a minted token resolves to its quoteId,
 * and survives a reopen of the service over the same repository (restart).
 */
@DataJpaTest
class PurchaseLinkServiceTest {

    @Autowired PurchaseTokenRepository repository;

    @Test
    void mintedTokenResolvesToQuoteId() {
        PurchaseLinkService service = new PurchaseLinkService(repository);
        String token = service.mintToken("quote-123");
        assertThat(service.resolve(token)).isEqualTo("quote-123");
    }

    @Test
    void unknownOrBlankTokenResolvesToNull() {
        PurchaseLinkService service = new PurchaseLinkService(repository);
        assertThat(service.resolve("nope")).isNull();
        assertThat(service.resolve("")).isNull();
        assertThat(service.resolve(null)).isNull();
    }

    @Test
    void tokenSurvivesAReopenOfTheService() {
        String token = new PurchaseLinkService(repository).mintToken("quote-xyz");
        // Reopen over the same persistent repository (simulates restart).
        PurchaseLinkService reopened = new PurchaseLinkService(repository);
        assertThat(reopened.resolve(token)).isEqualTo("quote-xyz");
    }
}
