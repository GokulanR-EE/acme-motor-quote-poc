package com.acme.platform.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for purchase token → quoteId mappings, keyed by token. */
public interface PurchaseTokenRepository extends JpaRepository<PurchaseTokenEntity, String> {
}
