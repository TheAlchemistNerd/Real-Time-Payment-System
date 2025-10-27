package com.paymentprocessor.common.exception;

public class TransactionNotFoundException extends PaymentProcessingException {
    public TransactionNotFoundException(String transactionId) {
        super("TRANSACTION_NOT_FOUND",
                "Transaction not found" + transactionId,
                "The requested transaction could not be found");
    }
}
