package com.fauzi.ledger.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SagaLogRepository
        extends JpaRepository<SagaLog, UUID> {

    List<SagaLog> findByTransferIdOrderByCreatedAtAsc(UUID transferId);
}
