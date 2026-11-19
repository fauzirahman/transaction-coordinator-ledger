package com.fauzi.ledger.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(

        @NotNull
        @JsonAlias({"from_account", "from_account_id"})
        UUID fromAccountId,

        @NotNull
        @JsonAlias({"to_account", "to_account_id"})
        UUID toAccountId,

        @NotNull
        @DecimalMin(value = "0.0001")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency,

        @NotBlank
        @Size(max = 100)
        String reference

) {
}
