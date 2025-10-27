package com.paymentprocessor.common.exception;

public abstract class PaymentProcessingException extends RuntimeException {
    private final String errorcode;
    private final String userMessage;

    protected PaymentProcessingException(String errorcode, String message, String userMessage) {
        super(message);
        this.errorcode = errorcode;
        this.userMessage = userMessage;
    }

    protected PaymentProcessingException(String errorcode, String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.errorcode = errorcode;
        this.userMessage = userMessage;
    }

    public String getErrorCode() {
        return errorcode;
    }

    public String getUserMessage() {
        return userMessage;
    }
}


