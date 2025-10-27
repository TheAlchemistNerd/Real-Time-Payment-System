package com.paymentprocessor.payment.service;

import com.paymentprocessor.payment.client.PaymentGatewayClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayFactory {

    private final PaymentGatewayClient stripeClient;
    private final PaymentGatewayClient payPalClient;

    public PaymentGatewayFactory(@Qualifier("stripe") PaymentGatewayClient stripeClient,
                                 @Qualifier("paypal") PaymentGatewayClient payPalClient) {
        this.stripeClient = stripeClient;
        this.payPalClient = payPalClient;
    }

    public PaymentGatewayClient getGateway(String gateway) {
        return switch (gateway.toLowerCase()) {
            case "stripe" -> stripeClient;
            case "paypal" -> payPalClient;
            default -> throw new IllegalArgumentException("Unknown gateway: " + gateway);
        };
    }
}
