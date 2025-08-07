package com.paymentprocessor.payment.command;

import jakarta.validation.constraints.NotNull;

public record ProcessFraudCheckCommand(
        @NotNull String transactionId,
        boolean fraudCheckPassed,
        double riskScore,
        String reason
) implements BaseCommand {

    public ProcessFraudCheckCommand {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or blank");
        }
        if (riskScore < 0.0 || riskScore > 1.0) {
            throw new IllegalArgumentException("Risk score must be between 0.0 and 1.0");
        }
    }
}
