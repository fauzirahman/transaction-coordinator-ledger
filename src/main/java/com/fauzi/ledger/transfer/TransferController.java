package com.fauzi.ledger.transfer;

import com.fauzi.ledger.transfer.dto.CreateTransferRequest;
import com.fauzi.ledger.transfer.dto.TransferDetailResponse;
import com.fauzi.ledger.transfer.dto.TransferResponse;
import com.fauzi.ledger.transfer.dto.TransferSummaryResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    public List<TransferSummaryResponse> getTransfers(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return transferService.list(userId(jwt), isAdmin(jwt));
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Transfer to create",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateTransferRequest.class),
                            examples = @ExampleObject(
                                    name = "Create transfer",
                                    value = """
                                            {
                                              "from_account_id": "a22fb083-627a-4635-a891-cb00351935f5",
                                              "to_account_id": "54cd90d7-abb4-479f-ab8e-6609736ef123",
                                              "amount": 100.00,
                                              "currency": "USD",
                                              "reference": "TRX-2026-0001"
                                            }
                                            """
                            )
                    )
            )
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
