package com.paymentprocessor.common.exception;

public class FraudDetectionException extends PaymentProcessingException {
    public FraudDetectionException(String message) {
        super("FRAUD_DETECTION_ERROR",
                "Fraud detection error: " + message,
                "Unable to verify transaction safety");
    }

    public FraudDetectionException(String message, Throwable cause) {
        super("FRAUD_DETECTION_ERROR",
                "Fraud detection error: " + message,
                "Unable to verify transaction safety", cause);
    }
}