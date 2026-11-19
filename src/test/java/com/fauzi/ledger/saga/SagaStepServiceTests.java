package com.fauzi.ledger.saga;

import com.fauzi.ledger.account.Account;
import com.fauzi.ledger.account.AccountRepository;
import com.fauzi.ledger.audit.AuditLogRepository;
import com.fauzi.ledger.ledger.LedgerEntry;
import com.fauzi.ledger.ledger.LedgerEntryRepository;
import com.fauzi.ledger.outbox.OutboxEventRepository;
import com.fauzi.ledger.transfer.Transfer;
import com.fauzi.ledger.transfer.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SagaStepServiceTests {

    private final UUID transferId = UUID.randomUUID();
    private final UUID fromId = UUID.randomUUID();
    private final UUID toId = UUID.randomUUID();
    private Account from;
    private Account to;
    private LedgerEntryRepository ledgerEntries;
    private SagaLogRepository sagaLogs;
    private SagaStepService service;
    private final List<LedgerEntry> savedEntries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        UUID ownerId = UUID.randomUUID();
        from = account(fromId, "from", ownerId, "50.00");
        to = account(toId, "to", UUID.randomUUID(), "0.00");
        Transfer transfer = new Transfer(
                from, to, new BigDecimal("10.00"), "USD", "ref-1", "key-1"
        );
        ReflectionTestUtils.setField(transfer, "id", transferId);

        TransferRepository transfers = mock(TransferRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        ledgerEntries = mock(LedgerEntryRepository.class);
        sagaLogs = mock(SagaLogRepository.class);
        when(transfers.findByIdForUpdate(transferId)).thenReturn(Optional.of(transfer));
        when(accounts.findByIdForUpdate(fromId)).thenReturn(Optional.of(from));
        when(accounts.findByIdForUpdate(toId)).thenReturn(Optional.of(to));
        when(sagaLogs.findByTransferIdOrderByCreatedAtAsc(transferId)).thenReturn(List.of());
        when(sagaLogs.save(any(SagaLog.class))).thenAnswer(call -> call.getArgument(0));
        when(ledgerEntries.save(any(LedgerEntry.class))).thenAnswer(call -> {
            LedgerEntry entry = call.getArgument(0);
            savedEntries.add(entry);
            return entry;
        });
        when(ledgerEntries.saveAndFlush(any(LedgerEntry.class))).thenAnswer(call -> {
            LedgerEntry entry = call.getArgument(0);
            savedEntries.add(entry);
            return entry;
        });
        when(ledgerEntries.findByTransferId(transferId)).thenReturn(savedEntries);
        service = new SagaStepService(
                transfers,
                accounts,
                ledgerEntries,
                sagaLogs,
                mock(AuditLogRepository.class),
                mock(OutboxEventRepository.class)
        );
    }

    @Test
    void postsBalancedDebitAndCreditAndUpdatesBothBalances() {
        service.postLedger(transferId);

        assertEquals(new BigDecimal("40.00"), from.getBalance());
        assertEquals(new BigDecimal("10.00"), to.getBalance());
        assertEquals(2, savedEntries.size());
        BigDecimal debits = savedEntries.stream()
                .filter(entry -> "DEBIT".equals(entry.getEntryType()))
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = savedEntries.stream()
                .filter(entry -> "CREDIT".equals(entry.getEntryType()))
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debits.compareTo(credits));
        assertTrue(savedEntries.stream().anyMatch(entry -> "DEBIT".equals(entry.getEntryType())));
        assertTrue(savedEntries.stream().anyMatch(entry -> "CREDIT".equals(entry.getEntryType())));
        verify(sagaLogs, times(2)).save(any(SagaLog.class));
    }

    @Test
    void rejectsInsufficientFundsWithoutWritingLedgerEntries() {
        ReflectionTestUtils.setField(from, "balance", new BigDecimal("5.00"));

        assertThrows(IllegalArgumentException.class, () -> service.postLedger(transferId));

        assertEquals(new BigDecimal("5.00"), from.getBalance());
        assertTrue(savedEntries.isEmpty());
        verify(ledgerEntries, never()).save(any(LedgerEntry.class));
        verify(ledgerEntries, never()).saveAndFlush(any(LedgerEntry.class));
    }

    private static Account account(UUID id, String number, UUID owner, String balance) {
        Account account = new Account(
                number, owner, number, "USD", new BigDecimal(balance)
        );
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
