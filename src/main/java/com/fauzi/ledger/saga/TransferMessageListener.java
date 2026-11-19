package com.fauzi.ledger.saga;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TransferMessageListener {

    private final TransferSagaService transferSagaService;

    public TransferMessageListener(TransferSagaService transferSagaService) {
        this.transferSagaService = transferSagaService;
    }

    @RabbitListener(queues = "transfer-requests")
    public void handleTransferRequest(String transferId) {
        transferSagaService.execute(UUID.fromString(transferId));
    }
}
