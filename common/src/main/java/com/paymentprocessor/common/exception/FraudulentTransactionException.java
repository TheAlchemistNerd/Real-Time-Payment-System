package com.paymentprocessor.common.exception;

public class FraudulentTransactionException extends PaymentProcessingException {
    private final double riskScore;

    public FraudulentTransactionException (double riskScore) {
        super("FRAUDULENT_TRANSACTION",
                "Transaction flagged as fraudulent with risk score: " + riskScore,
                "Transaction has been declined for security reasons");
        this.riskScore = riskScore;
    }

    public double getRiskScore() {
        return riskScore;
    }
}
