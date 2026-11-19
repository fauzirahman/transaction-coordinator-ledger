package com.fauzi.ledger.transfer.dto;

import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        String reference,
        String status,
        String message
) {
}
