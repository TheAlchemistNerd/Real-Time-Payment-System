package com.paymentprocessor.fraud.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FraudCheckResponse {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("risk_score")
    private double riskScore;

    @JsonProperty("decision")
    private String decision; // "APPROVE", "DECLINE", "REVIEW"

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("confidence")
    private double confidence;

    public FraudCheckResponse() {}

    public FraudCheckResponse(String transactionId, double riskScore, String decision,
                              String reason, double confidence) {
        this.transactionId = transactionId;
        this.riskScore = riskScore;
        this.decision = decision;
        this.reason = reason;
        this.confidence = confidence;
    }

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double riskScore) { this.riskScore = riskScore; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
