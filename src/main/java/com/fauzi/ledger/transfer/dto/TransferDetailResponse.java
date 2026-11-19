package com.fauzi.ledger.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransferDetailResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String reference,
        String status,
        String failureReason,
        LocalDateTime createdAt,
        List<LedgerEntryResponse> ledgerEntries
) {
}
