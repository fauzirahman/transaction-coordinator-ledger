package com.fauzi.ledger.saga;

import com.fauzi.ledger.account.Account;
import com.fauzi.ledger.account.AccountRepository;
import com.fauzi.ledger.audit.AuditLog;
import com.fauzi.ledger.audit.AuditLogRepository;
import com.fauzi.ledger.ledger.LedgerEntry;
import com.fauzi.ledger.ledger.LedgerEntryRepository;
import com.fauzi.ledger.outbox.OutboxEvent;
import com.fauzi.ledger.outbox.OutboxEventRepository;
import com.fauzi.ledger.transfer.Transfer;
import com.fauzi.ledger.transfer.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SagaStepService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final SagaLogRepository sagaLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final OutboxEventRepository outboxEventRepository;

    public SagaStepService(
            TransferRepository transferRepository,
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            SagaLogRepository sagaLogRepository,
            AuditLogRepository auditLogRepository,
            OutboxEventRepository outboxEventRepository
    ) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.sagaLogRepository = sagaLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void postLedger(UUID transferId) {
        Transfer transfer = lockTransfer(transferId);
        if ((hasCompletedStep(transferId, "DEBIT")
                && hasCompletedStep(transferId, "CREDIT"))
                || isTerminal(transfer)) {
            return;
        }
        SagaLog debitLog = startLog(transfer, "DEBIT");
        SagaLog creditLog = startLog(transfer, "CREDIT");
        List<Account> accounts = List.of(
                transfer.getFromAccount().getId(),
                transfer.getToAccount().getId()
        ).stream().sorted(Comparator.naturalOrder())
                .map(this::lockAccount)
                .toList();
        Map<UUID, Account> accountsById = accounts.stream()
                .collect(java.util.stream.Collectors.toMap(Account::getId, account -> account));
        Account fromAccount = accountsById.get(transfer.getFromAccount().getId());
        Account toAccount = accountsById.get(transfer.getToAccount().getId());
        if (!"ACTIVE".equals(fromAccount.getStatus()) || !"ACTIVE".equals(toAccount.getStatus())) {
            throw new IllegalArgumentException("Both accounts must be active");
        }
        if (!transfer.getCurrency().equalsIgnoreCase(fromAccount.getCurrency())
                || !transfer.getCurrency().equalsIgnoreCase(toAccount.getCurrency())) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        fromAccount.debit(transfer.getAmount());
        toAccount.credit(transfer.getAmount());
        ledgerEntryRepository.save(new LedgerEntry(
                transfer, fromAccount, "DEBIT", transfer.getAmount(), transfer.getCurrency(),
                "Transfer debit"
        ));
        ledgerEntryRepository.saveAndFlush(new LedgerEntry(
                transfer, toAccount, "CREDIT", transfer.getAmount(), transfer.getCurrency(),
                "Transfer credit"
        ));
        verifyBalanced(transferId);
        transfer.markProcessing();
        debitLog.markCompleted();
        creditLog.markCompleted();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyCompletion(UUID transferId) {
        Transfer transfer = lockTransfer(transferId);
        if (hasCompletedStep(transferId, "NOTIFY") || isTerminal(transfer)) {
            return;
        }
        SagaLog log = startLog(transfer, "NOTIFY");
        outboxEventRepository.save(new OutboxEvent(
                "TRANSFER",
                transferId,
                "TRANSFER_COMPLETED",
                Map.of("transferId", transferId.toString(), "reference", transfer.getReference())
        ));
        log.markCompleted();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void auditCompletion(UUID transferId) {
        Transfer transfer = lockTransfer(transferId);
        if (hasCompletedStep(transferId, "AUDIT") || isTerminal(transfer)) {
            return;
        }
        SagaLog log = startLog(transfer, "AUDIT");
        auditLogRepository.save(new AuditLog(
                transfer,
                transfer.getFromAccount().getOwnerUserId(),
                "TRANSFER_COMPLETED",
                Map.of("reference", transfer.getReference(), "amount", transfer.getAmount())
        ));
        transfer.markCompleted();
        log.markCompleted();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID transferId, String step, String message) {
        Transfer transfer = lockTransfer(transferId);
        if (isTerminal(transfer)) {
            return;
        }
        SagaLog log = startLog(transfer, step);
        log.markFailed(message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(UUID transferId, String reason) {
        Transfer transfer = lockTransfer(transferId);
        if ("COMPENSATED".equals(transfer.getStatus())) {
            return;
        }
        SagaLog compensationLog = startLog(transfer, "COMPENSATION");
        compensationLog.markProcessing();

        List<Account> accounts = List.of(
                transfer.getFromAccount().getId(),
                transfer.getToAccount().getId()
        ).stream().distinct().sorted(Comparator.naturalOrder())
                .map(this::lockAccount)
                .toList();
        Map<UUID, Account> accountsById = accounts.stream()
                .collect(java.util.stream.Collectors.toMap(Account::getId, account -> account));

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransferId(transferId);
        for (LedgerEntry entry : entries) {
            Account account = accountsById.get(entry.getAccount().getId());
            String reversalType = "DEBIT".equals(entry.getEntryType()) ? "CREDIT" : "DEBIT";
            if ("DEBIT".equals(reversalType)) {
                account.debit(entry.getAmount());
            } else {
                account.credit(entry.getAmount());
            }
            ledgerEntryRepository.save(new LedgerEntry(
                    transfer,
                    account,
                    reversalType,
                    entry.getAmount(),
                    entry.getCurrency(),
                    "Compensation: " + entry.getEntryType().toLowerCase()
            ));
        }
        ledgerEntryRepository.flush();
        verifyBalanced(transferId);
        transfer.markCompensated(reason);
        compensationLog.markCompensated();
        auditLogRepository.save(new AuditLog(
                transfer,
                transfer.getFromAccount().getOwnerUserId(),
                "TRANSFER_COMPENSATED",
                Map.of("reason", reason)
        ));
        outboxEventRepository.save(new OutboxEvent(
                "TRANSFER",
                transferId,
                "TRANSFER_COMPENSATED",
                Map.of("transferId", transferId.toString(), "reason", reason)
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean hasDebit(UUID transferId) {
        return ledgerEntryRepository.findByTransferId(transferId).stream()
                .anyMatch(entry -> "DEBIT".equals(entry.getEntryType()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID transferId, String reason) {
        Transfer transfer = lockTransfer(transferId);
        if (!isTerminal(transfer)) {
            transfer.markFailed(reason);
            auditLogRepository.save(new AuditLog(
                    transfer,
                    transfer.getFromAccount().getOwnerUserId(),
                    "TRANSFER_FAILED",
                    Map.of("reason", reason)
            ));
        }
    }

    private Transfer lockTransfer(UUID transferId) {
        return transferRepository.findByIdForUpdate(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
    }

    private Account lockAccount(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private SagaLog startLog(Transfer transfer, String step) {
        SagaLog log = new SagaLog(transfer, step);
        log.markProcessing();
        return sagaLogRepository.save(log);
    }

    private boolean hasCompletedStep(UUID transferId, String step) {
        return sagaLogRepository.findByTransferIdOrderByCreatedAtAsc(transferId).stream()
                .anyMatch(log -> step.equals(log.getStepName())
                        && ("COMPLETED".equals(log.getStepStatus())
                        || "COMPENSATED".equals(log.getStepStatus())));
    }

    private boolean isTerminal(Transfer transfer) {
        return List.of("COMPLETED", "FAILED", "COMPENSATED").contains(transfer.getStatus());
    }

    private void verifyBalanced(UUID transferId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransferId(transferId);
        BigDecimal debits = entries.stream()
                .filter(entry -> "DEBIT".equals(entry.getEntryType()))
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entries.stream()
                .filter(entry -> "CREDIT".equals(entry.getEntryType()))
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException("Ledger entries are not balanced");
        }
    }
}
