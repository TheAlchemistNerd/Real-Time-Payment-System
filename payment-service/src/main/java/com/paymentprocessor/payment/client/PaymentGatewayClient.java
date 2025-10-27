package com.paymentprocessor.payment.client;

import com.paymentprocessor.payment.dto.PaymentRequest;
import com.paymentprocessor.payment.dto.PaymentResponse;
import com.paymentprocessor.payment.dto.RefundRequest;
import com.paymentprocessor.payment.dto.RefundResponse;
import reactor.core.publisher.Mono;

public interface PaymentGatewayClient {
    Mono<PaymentResponse> processPayment(PaymentRequest request);
    Mono<RefundResponse> refundPayment(RefundRequest request);
    Mono<String> getPaymentStatus(String transactionId);
}
