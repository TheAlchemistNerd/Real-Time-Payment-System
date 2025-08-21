package com.paymentprocessor.notification.service;

import com.paymentprocessor.notification.model.NotificationMessage;
import com.paymentprocessor.notification.model.WebhookPayload;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
@Slf4j
@Service
public class WebhookNotificationService implements NotificationService {

    private final WebClient webClient;

    @Autowired
    public WebhookNotificationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    @Async
    @Retry(name = "webhookNotification")
    @Timed(value = "notification.webhook.send.time", description = "Time taken to send webhook notifications")
    @Counted(value = "notification.webhook.send.count", description = "Number of webhook notifications sent")
    public CompletableFuture<Boolean> sendNotification(NotificationMessage message) {
        log.info("Sending webhook notification for transaction: {} to: {}",
                message.getTransactionId(), message.getRecipient());

        try {
            Map<String, Object> transactionData = new HashMap<>();
            transactionData.put("transactionId", message.getTransactionId());
            transactionData.put("userId", message.getUserId());
            transactionData.put("status", message.getTransactionStatus());
            transactionData.put("amount", message.getAmount());

            WebhookPayload payload = new WebhookPayload(
                    message.getTransactionId(),
                    message.getTransactionStatus().toString(),
                    message.getContent(),
                    transactionData
            );

            return webClient.post()
                    .uri(message.getRecipient()) // The recipient field contains the webhook URL
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .map(response -> {
                        log.info("Webhook sent successfully for transaction: {} to: {}",
                                message.getTransactionId(), message.getRecipient());
                        // On success, the stream emits true
                        return true;
                    })
                    // This now correctly returns a Mono<Boolean> on error
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.warn("Webhook failed for transaction: {} with status: {}. Reason: {}",
                                message.getTransactionId(), ex.getStatusCode(), ex.getResponseBodyAsString());
                        return Mono.just(false); // <-- CORRECTED
                    })
                    // This generic error handler also returns a Mono<Boolean>
                    .onErrorResume(Exception.class, ex -> {
                        log.error("Webhook error for transaction: {}", message.getTransactionId(), ex);
                        return Mono.just(false); // <-- CORRECTED
                    })
                    .toFuture(); // Converts the final Mono<Boolean> to a CompletableFuture<Boolean>
        } catch (Exception e) {
            log.error("Failed to send webhook notification for transaction: {}",
                    message.getTransactionId(), e);
            return CompletableFuture.completedFuture(false);
        }
    }
}