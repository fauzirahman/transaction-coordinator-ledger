package com.fauzi.ledger.saga;

import com.fauzi.ledger.account.Account;
import com.fauzi.ledger.transfer.Transfer;
import com.fauzi.ledger.transfer.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class TransferSagaServiceTests {

    private final UUID transferId = UUID.randomUUID();
    private SagaStepService steps;
    private TransferRepository transferRepository;
    private TransferSagaService service;
    private Transfer transfer;

    @BeforeEach
    void setUp() {
        steps = mock(SagaStepService.class);
        transferRepository = mock(TransferRepository.class);
        service = new TransferSagaService(steps, transferRepository);
        UUID ownerId = UUID.randomUUID();
        Account from = new Account("from", ownerId, "From", "USD", new BigDecimal("50.00"));
        Account to = new Account("to", UUID.randomUUID(), "To", "USD", BigDecimal.ZERO);
        transfer = new Transfer(
                from, to, new BigDecimal("10.00"), "USD", "ref-1", "key-1"
        );
        when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));
    }

    @Test
    void runsLedgerNotificationAndAuditPhasesInOrder() {
        service.execute(transferId);

        var order = inOrder(steps);
        order.verify(steps).postLedger(transferId);
        order.verify(steps).notifyCompletion(transferId);
        order.verify(steps).auditCompletion(transferId);
        verifyNoMoreInteractions(steps);
    }

    @Test
    void compensatesWhenANonLedgerStepFails() {
        doThrow(new IllegalStateException("notification unavailable"))
                .when(steps).notifyCompletion(transferId);
        when(steps.hasDebit(transferId)).thenReturn(true);

        service.execute(transferId);

        verify(steps).recordFailure(transferId, "NOTIFY", "notification unavailable");
        verify(steps).compensate(transferId, "notification unavailable");
        verify(steps, never()).auditCompletion(transferId);
    }

    @Test
    void marksTransferFailedWhenLedgerPostingFailsBeforeDebitCommits() {
        doThrow(new IllegalArgumentException("insufficient balance"))
                .when(steps).postLedger(transferId);
        when(steps.hasDebit(transferId)).thenReturn(false);

        service.execute(transferId);

        verify(steps).recordFailure(transferId, "DEBIT", "insufficient balance");
        verify(steps).markFailed(transferId, "insufficient balance");
        verify(steps, never()).compensate(any(), anyString());
    }

    @Test
    void skipsAlreadyCompletedTransfers() {
        transfer.markCompleted();

        service.execute(transferId);

        verifyNoInteractions(steps);
    }
}
