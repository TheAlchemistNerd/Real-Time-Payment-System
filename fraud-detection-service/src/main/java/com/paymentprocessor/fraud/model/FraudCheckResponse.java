package com.paymentprocessor.fraud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Represents a response from an external fraud detection API.
 * This record includes validation constraints for the received data.
 */

public record FraudCheckResponse(
        @NotNull(message = "Transaction ID cannot be null")
        @NotBlank(message = "Transaction ID cannot be blank")
        @JsonProperty("transaction_id") String transactionId,

        @Min(value = 0, message = "Risk score cannot be less than 0")
        @Max(value = 1, message = "Risk score cannot be greater than 1")
        @JsonProperty("risk_score") double riskScore,

        @NotNull(message = "Decision cannot be null")
        @NotBlank(message = "Decision cannot be blank")
        @Pattern(regexp = "^(APPROVE|DECLINE|REVIEW)$", message = "Decision must be APPROVE, DECLINE, or REVIEW")
        @JsonProperty("decision") String decision, // "APPROVE", "DECLINE", "REVIEW"

        @JsonProperty("reason") String reason, // Optional, can be null

        @Min(value = 0, message = "Confidence cannot be less than 0")
        @Max(value = 1, message = "Confidence cannot be greater than 1")
        @JsonProperty("confidence") double confidence
) {
    // Compact constructor for validation logic
    public FraudCheckResponse {
        // Ensure decision is always uppercase for consistency, even if received in mixed case.
        if (decision != null) {
            decision = decision.toUpperCase();
        }
    }
}