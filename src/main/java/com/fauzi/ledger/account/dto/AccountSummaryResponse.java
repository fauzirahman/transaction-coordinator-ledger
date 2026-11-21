package com.fauzi.ledger.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountSummaryResponse(
        UUID accountId,
        String accountNumber,
        String accountName,
        String currency,
        BigDecimal balance,
        String status
) {
}
