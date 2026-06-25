package com.acme.platform.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the append-only domain-event log. */
public interface DomainEventRepository extends JpaRepository<DomainEventEntity, Long> {

    /** All events in append (seq) order — the replay stream. */
    List<DomainEventEntity> findAllByOrderBySeqAsc();
}
