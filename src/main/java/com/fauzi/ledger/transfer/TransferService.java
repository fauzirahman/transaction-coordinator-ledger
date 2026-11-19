package com.fauzi.ledger.transfer;

import com.fauzi.ledger.account.Account;
import com.fauzi.ledger.account.AccountRepository;
import com.fauzi.ledger.audit.AuditLog;
import com.fauzi.ledger.audit.AuditLogRepository;
import com.fauzi.ledger.ledger.LedgerEntry;
import com.fauzi.ledger.ledger.LedgerEntryRepository;
import com.fauzi.ledger.outbox.OutboxEvent;
import com.fauzi.ledger.outbox.OutboxEventRepository;
import com.fauzi.ledger.transfer.dto.CreateTransferRequest;
import com.fauzi.ledger.transfer.dto.LedgerEntryResponse;
import com.fauzi.ledger.transfer.dto.TransferDetailResponse;
import com.fauzi.ledger.transfer.dto.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditLogRepository auditLogRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerEntryRepository ledgerEntryRepository,
            OutboxEventRepository outboxEventRepository,
            AuditLogRepository auditLogRepository
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public TransferResponse createTransfer(
            CreateTransferRequest request,
            String idempotencyKey,
            UUID actorUserId
    ) {
        var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Transfer transfer = existing.get();
            if (!transfer.getFromAccount().getOwnerUserId().equals(actorUserId)) {
                throw new TransferNotFoundException("Transfer not found");
            }
            boolean sameRequest =
                    transfer.getFromAccount().getId().equals(request.fromAccountId())
                    && transfer.getToAccount().getId().equals(request.toAccountId())
                    && transfer.getAmount().compareTo(request.amount()) == 0
                    && transfer.getCurrency().equalsIgnoreCase(request.currency())
                    && transfer.getReference().equals(request.reference());
            if (!sameRequest) {
                throw new TransferConflictException(
                        "Idempotency key already used with a different request"
                );
            }
            return new TransferResponse(
                    transfer.getId(), transfer.getReference(), transfer.getStatus(),
                    "Transfer request already exists"
            );
        }

        Account fromAccount = accountRepository.findById(request.fromAccountId())
                .orElseThrow(() -> new TransferNotFoundException("Source account not found"));
        Account toAccount = accountRepository.findById(request.toAccountId())
                .orElseThrow(() -> new TransferNotFoundException("Destination account not found"));
        if (!fromAccount.getOwnerUserId().equals(actorUserId)) {
            throw new TransferNotFoundException("Source account not found");
        }
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }
        if (!"ACTIVE".equals(fromAccount.getStatus()) || !"ACTIVE".equals(toAccount.getStatus())) {
            throw new IllegalArgumentException("Both accounts must be active");
        }
        if (!fromAccount.getCurrency().equalsIgnoreCase(request.currency())
                || !toAccount.getCurrency().equalsIgnoreCase(request.currency())) {
            throw new IllegalArgumentException("Currency mismatch");
        }

        Transfer transfer = new Transfer(
                fromAccount, toAccount, request.amount(),
                request.currency().toUpperCase(), request.reference(), idempotencyKey
        );
        transferRepository.saveAndFlush(transfer);
        outboxEventRepository.save(new OutboxEvent(
                "TRANSFER",
                transfer.getId(),
                "TRANSFER_REQUESTED",
                Map.of("transferId", transfer.getId().toString())
        ));
        auditLogRepository.save(new AuditLog(
                transfer,
                actorUserId,
                "TRANSFER_REQUESTED",
                Map.of("reference", transfer.getReference())
        ));
        return new TransferResponse(
                transfer.getId(), transfer.getReference(), transfer.getStatus(),
                "Transfer request accepted"
        );
    }

    @Transactional(readOnly = true)
    public TransferDetailResponse getById(UUID transferId, UUID actorUserId, boolean admin) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found"));
        requireTransferAccess(transfer, actorUserId, admin);
        return toDetail(transfer);
    }

    @Transactional(readOnly = true)
    public TransferDetailResponse getByReference(String reference, UUID actorUserId, boolean admin) {
        Transfer transfer = transferRepository.findByReference(reference)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found"));
        requireTransferAccess(transfer, actorUserId, admin);
        return toDetail(transfer);
    }

    private void requireTransferAccess(Transfer transfer, UUID actorUserId, boolean admin) {
        if (!admin
                && !transfer.getFromAccount().getOwnerUserId().equals(actorUserId)
                && !transfer.getToAccount().getOwnerUserId().equals(actorUserId)) {
            throw new TransferNotFoundException("Transfer not found");
        }
    }

    private TransferDetailResponse toDetail(Transfer transfer) {
        List<LedgerEntryResponse> entries = ledgerEntryRepository
                .findByTransferId(transfer.getId())
                .stream()
                .map(TransferService::toLedgerEntry)
                .toList();
        return new TransferDetailResponse(
                transfer.getId(),
                transfer.getFromAccount().getId(),
                transfer.getToAccount().getId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getReference(),
                transfer.getStatus(),
                transfer.getFailureReason(),
                transfer.getCreatedAt(),
                entries
        );
    }

    public static LedgerEntryResponse toLedgerEntry(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccount().getId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getDescription(),
                entry.getCreatedAt()
        );
    }
}
