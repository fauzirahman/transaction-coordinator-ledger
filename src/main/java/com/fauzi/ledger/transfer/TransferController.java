package com.fauzi.ledger.transfer;

import com.fauzi.ledger.transfer.dto.CreateTransferRequest;
import com.fauzi.ledger.transfer.dto.TransferDetailResponse;
import com.fauzi.ledger.transfer.dto.TransferResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must be 1 to 100 characters");
        }
        TransferResponse response =
                transferService.createTransfer(request, idempotencyKey, userId(jwt));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    public TransferDetailResponse getTransfer(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return transferService.getById(id, userId(jwt), isAdmin(jwt));
    }

    @GetMapping("/reference/{reference}")
    public TransferDetailResponse getByReference(
            @PathVariable String reference,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return transferService.getByReference(reference, userId(jwt), isAdmin(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static boolean isAdmin(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }
}
