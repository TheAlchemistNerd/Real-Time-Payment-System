package com.paymentprocessor.payment.client;

import com.paymentprocessor.payment.dto.PaymentRequest;
import com.paymentprocessor.payment.dto.PaymentResponse;
import com.paymentprocessor.payment.dto.RefundRequest;
import com.paymentprocessor.payment.dto.RefundResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component("paypal")
public class PayPalClient implements PaymentGatewayClient {

    @Override
    public Mono<PaymentResponse> processPayment(PaymentRequest request) {
        return Mono.just(new PaymentResponse("paypal-" + System.currentTimeMillis(), true, "Payment successful"));
    }

    @Override
    public Mono<RefundResponse> refundPayment(RefundRequest request) {
        return Mono.just(new RefundResponse("paypal-refund-" + System.currentTimeMillis(), true, "Refund successful"));
    }

    @Override
    public Mono<String> getPaymentStatus(String transactionId) {
        return Mono.just("COMPLETED");
    }
}
