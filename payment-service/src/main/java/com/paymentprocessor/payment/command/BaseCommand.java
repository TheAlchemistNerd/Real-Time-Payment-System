package com.paymentprocessor.payment.command;

public sealed interface BaseCommand permits
        CreateTransactionCommand,
        ProcessFraudCheckCommand,
        ProcessPaymentCommand
{
    String transactionId();
}
