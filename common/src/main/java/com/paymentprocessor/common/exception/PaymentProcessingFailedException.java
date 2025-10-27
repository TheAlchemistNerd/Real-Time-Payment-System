package com.paymentprocessor.common.exception;

public class PaymentProcessingFailedException extends PaymentProcessingException {
    public PaymentProcessingFailedException (String message) {
        super("PAYMENT PROCESSING FAILED",
                "Payment processing failed: " + message,
                "Payment could not be processed at this time");
    }

    public PaymentProcessingFailedException (String message, Throwable cause) {
        super("PAYMENT PROCESSING FAILED",
                "Payment processing failed: " + message,
                "Payment could not be processed at this time", cause);
    }
}
