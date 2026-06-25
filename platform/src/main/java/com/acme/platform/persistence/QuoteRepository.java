package com.acme.platform.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persisted quotes, keyed by quoteId. */
public interface QuoteRepository extends JpaRepository<QuoteEntity, String> {
}
