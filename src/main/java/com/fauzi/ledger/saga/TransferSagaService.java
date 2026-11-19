package com.fauzi.ledger.saga;

import com.fauzi.ledger.transfer.TransferRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TransferSagaService {

    private final SagaStepService steps;
    private final TransferRepository transferRepository;

    public TransferSagaService(SagaStepService steps, TransferRepository transferRepository) {
        this.steps = steps;
        this.transferRepository = transferRepository;
    }

    public void execute(UUID transferId) {
        var transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (isTerminal(transfer.getStatus())) {
            return;
        }
        String currentStep = "DEBIT";
        try {
            steps.postLedger(transferId);
            currentStep = "NOTIFY";
            steps.notifyCompletion(transferId);
            currentStep = "AUDIT";
            steps.auditCompletion(transferId);
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            steps.recordFailure(transferId, currentStep, reason);
            if (steps.hasDebit(transferId)) {
                try {
                    steps.compensate(transferId, reason);
                } catch (RuntimeException compensationFailure) {
                    steps.recordFailure(
                            transferId,
                            "COMPENSATION",
                            compensationFailure.getMessage() == null
                                    ? compensationFailure.getClass().getSimpleName()
                                    : compensationFailure.getMessage()
                    );
                    steps.markFailed(
                            transferId,
                            "Compensation failed: " + compensationFailure.getMessage()
                    );
                    throw compensationFailure;
                }
            } else {
                steps.markFailed(transferId, reason);
            }
        }
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status)
                || "COMPENSATED".equals(status);
    }
}
