package com.paymentprocessor.common.exception;

public class InvalidTransactionException extends PaymentProcessingException {
    public InvalidTransactionException (String message) {
        super("INVALID TRANSACTION",
                "Invalid transaction: " + message,
                "The transaction request is invalid");
    }

    public InvalidTransactionException (String message, Throwable cause) {
        super("INVALID TRANSACTION",
                "Invalid transaction: " + message,
                "The transaction request is invalid", cause);
    }
}
