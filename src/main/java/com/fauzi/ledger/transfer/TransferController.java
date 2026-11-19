package com.fauzi.ledger.transfer;

import com.fauzi.ledger.transfer.dto.CreateTransferRequest;
import com.fauzi.ledger.transfer.dto.TransferResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {

        TransferResponse response =
                transferService.createTransfer(request, idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }
}
