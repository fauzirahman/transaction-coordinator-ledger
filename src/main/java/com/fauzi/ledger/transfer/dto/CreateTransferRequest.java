package com.fauzi.ledger.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(

        @NotNull
        UUID fromAccountId,

        @NotNull
        UUID toAccountId,

        @NotNull
        @DecimalMin(value = "0.0001")
        BigDecimal amount,

        @NotBlank
        String currency,

        @NotBlank
        String reference

) {
}
