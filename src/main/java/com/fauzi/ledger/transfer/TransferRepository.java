package com.fauzi.ledger.transfer;

import com.fauzi.ledger.transfer.dto.TransferSummaryResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByReference(String reference);

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT new com.fauzi.ledger.transfer.dto.TransferSummaryResponse(
                t.id, t.fromAccount.id, t.toAccount.id, t.amount, t.currency,
                t.reference, t.status, t.createdAt
            )
            FROM Transfer t
            WHERE t.fromAccount.ownerUserId = :ownerId
                OR t.toAccount.ownerUserId = :ownerId
            ORDER BY t.createdAt DESC
            """)
    List<TransferSummaryResponse> findSummariesVisibleToOwner(
            @Param("ownerId") UUID ownerId
    );

    @Query("""
            SELECT new com.fauzi.ledger.transfer.dto.TransferSummaryResponse(
                t.id, t.fromAccount.id, t.toAccount.id, t.amount, t.currency,
                t.reference, t.status, t.createdAt
            )
            FROM Transfer t
            ORDER BY t.createdAt DESC
            """)
    List<TransferSummaryResponse> findAllSummaries();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transfer t WHERE t.id = :id")
    Optional<Transfer> findByIdForUpdate(@Param("id") UUID id);
}
