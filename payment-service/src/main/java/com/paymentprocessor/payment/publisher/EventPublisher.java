package com.paymentprocessor.payment.publisher;

import com.paymentprocessor.common.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<Void> publishAsync(String topic, String key, BaseEvent event) {
        log.debug("Publishing event {} to topic {} with key {}",
                event.getClass().getSimpleName(), topic, key);

        return kafkaTemplate.send(topic, key, event)
                .thenAccept(result ->
                        log.debug("Successfully published {} to topic {}",
                                event.getClass().getSimpleName(), topic))
                .exceptionally(throwable -> {
                    log.error("Failed to publish {} to topic {}",
                            event.getClass().getSimpleName(), topic, throwable);
                    throw new RuntimeException("Event publishing failed", throwable);
                });
    }

    public void publish(String topic, String key, BaseEvent event) {
        publishAsync(topic, key, event).join(); // Blocking version if needed
    }
}