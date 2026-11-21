package com.fauzi.ledger.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(

        @NotNull
        @JsonProperty("from_account_id")
        @JsonAlias({"fromAccountId", "from_account"})
        UUID fromAccountId,

        @NotNull
        @JsonProperty("to_account_id")
        @JsonAlias({"toAccountId", "to_account"})
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
