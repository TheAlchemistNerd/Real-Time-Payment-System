package com.paymentprocessor.common.command;

public sealed interface BaseCommand permits
        CreateTransactionCommand,
        ProcessFraudCheckCommand,
        ProcessPaymentCommand
{
    String transactionId();
}
