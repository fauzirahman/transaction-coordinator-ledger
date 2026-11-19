package com.fauzi.ledger.transfer;

import com.fauzi.ledger.account.Account;
import com.fauzi.ledger.account.AccountRepository;
import com.fauzi.ledger.outbox.OutboxEvent;
import com.fauzi.ledger.outbox.OutboxEventRepository;
import com.fauzi.ledger.transfer.dto.CreateTransferRequest;
import com.fauzi.ledger.transfer.dto.TransferResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final OutboxEventRepository outboxEventRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            OutboxEventRepository outboxEventRepository
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public TransferResponse createTransfer(
            CreateTransferRequest request,
            String idempotencyKey
    ) {

        // 1. Check idempotency
        var existing = transferRepository
                .findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            Transfer transfer = existing.get();

            boolean sameRequest =
                    transfer.getFromAccount().getId()
                            .equals(request.fromAccountId())
                    && transfer.getToAccount().getId()
                            .equals(request.toAccountId())
                    && transfer.getAmount().compareTo(request.amount()) == 0
                    && transfer.getCurrency().equalsIgnoreCase(request.currency())
                    && transfer.getReference().equals(request.reference());

            if (!sameRequest) {
                throw new IllegalArgumentException(
                        "Idempotency key already used with different request"
                );
            }

            return new TransferResponse(
                    transfer.getId(),
                    transfer.getReference(),
                    transfer.getStatus(),
                    "Transfer request already exists"
            );
        }

        // 2. Validate accounts
        Account fromAccount = accountRepository
                .findById(request.fromAccountId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Source account not found")
                );

        Account toAccount = accountRepository
                .findById(request.toAccountId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Destination account not found")
                );

        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different"
            );
        }

        if (!"ACTIVE".equals(fromAccount.getStatus())
                || !"ACTIVE".equals(toAccount.getStatus())) {
            throw new IllegalArgumentException("Account is not active");
        }

        if (!fromAccount.getCurrency().equalsIgnoreCase(request.currency())
                || !toAccount.getCurrency().equalsIgnoreCase(request.currency())) {
            throw new IllegalArgumentException("Currency mismatch");
        }

        // 3. Create transfer
        Transfer transfer = new Transfer(
                fromAccount,
                toAccount,
                request.amount(),
                request.currency().toUpperCase(),
                request.reference(),
                idempotencyKey
        );

        transferRepository.saveAndFlush(transfer);

        // 4. Save event to transactional outbox
        OutboxEvent event = new OutboxEvent(
                "TRANSFER",
                transfer.getId(),
                "TRANSFER_REQUESTED",
                Map.of(
                        "transferId", transfer.getId().toString(),
                        "reference", transfer.getReference()
                )
        );

        outboxEventRepository.save(event);

        // 5. Return accepted request
        return new TransferResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getStatus(),
                "Transfer request accepted"
        );
    }
}
