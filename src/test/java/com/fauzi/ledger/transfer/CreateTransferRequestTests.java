package com.fauzi.ledger.transfer;

import com.fauzi.ledger.transfer.dto.CreateTransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateTransferRequestTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsDocumentedSnakeCaseAndExistingCamelCaseFields() throws Exception {
        String fromAccountId = "a22fb083-627a-4635-a891-cb00351935f5";
        String toAccountId = "54cd90d7-abb4-479f-ab8e-6609736ef123";

        CreateTransferRequest snakeCase = objectMapper.readValue("""
                {
                  "from_account_id": "%s",
                  "to_account_id": "%s",
                  "amount": 100.00,
                  "currency": "USD",
                  "reference": "TRX-2026-0001"
                }
                """.formatted(fromAccountId, toAccountId), CreateTransferRequest.class);
        CreateTransferRequest camelCase = objectMapper.readValue("""
                {
                  "fromAccountId": "%s",
                  "toAccountId": "%s",
                  "amount": 100.00,
                  "currency": "USD",
                  "reference": "TRX-2026-0001"
                }
                """.formatted(fromAccountId, toAccountId), CreateTransferRequest.class);

        assertEquals(snakeCase, camelCase);
    }
}
