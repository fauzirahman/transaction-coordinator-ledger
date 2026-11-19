package com.fauzi.ledger.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        String entryType,
        BigDecimal amount,
        String currency,
        String description,
        LocalDateTime createdAt
) {
}
