package com.fauzi.ledger.saga;

import com.fauzi.ledger.transfer.TransferNotFoundException;
import com.fauzi.ledger.transfer.TransferRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/saga/log")
@SecurityRequirement(name = "bearerAuth")
public class SagaController {

    private final SagaLogRepository sagaLogRepository;
    private final TransferRepository transferRepository;

    public SagaController(SagaLogRepository sagaLogRepository, TransferRepository transferRepository) {
        this.sagaLogRepository = sagaLogRepository;
        this.transferRepository = transferRepository;
    }

    @GetMapping("/{transferId}")
    @Transactional(readOnly = true)
    public List<SagaStepResponse> getSagaLog(
            @PathVariable UUID transferId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        var transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found"));
        var roles = jwt.getClaimAsStringList("roles");
        if ((roles == null || !roles.contains("ADMIN"))
                && !transfer.getFromAccount().getOwnerUserId().toString().equals(jwt.getSubject())
                && !transfer.getToAccount().getOwnerUserId().toString().equals(jwt.getSubject())) {
            throw new TransferNotFoundException("Transfer not found");
        }
        return sagaLogRepository.findByTransferIdOrderByCreatedAtAsc(transferId)
                .stream()
                .map(log -> new SagaStepResponse(
                        log.getStepName(), log.getStepStatus(), log.getErrorMessage(),
                        log.getStartedAt(), log.getCompletedAt(), log.getCreatedAt()
                ))
                .toList();
    }

    public record SagaStepResponse(
            String step,
            String status,
            String error,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime createdAt
    ) {
    }
}
