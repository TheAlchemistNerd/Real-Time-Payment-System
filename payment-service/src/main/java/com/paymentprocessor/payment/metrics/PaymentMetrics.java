package com.paymentprocessor.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final Timer commandProcessingTimer;
    private final Counter commandSuccessCounter;
    private final Counter commandFailureCounter;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.commandProcessingTimer = Timer.builder("payment.command.processing.duration")
                .description("Time taken to process commands")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.commandSuccessCounter = Counter.builder("payment.command.processing.success")
                .description("Number of successful command executions")
                .register(meterRegistry);

        this.commandFailureCounter = Counter.builder("payment.command.processing.failure")
                .description("Number of failed command executions")
                .register(meterRegistry);
    }

    public Timer getCommandProcessingTimer() {
        return commandProcessingTimer;
    }

    public void recordCommandSuccess(String commandType) {
        commandSuccessCounter.increment();
        // In a real scenario, you might add a tag for the commandType
        // e.g., meterRegistry.counter("payment.command.processing.success", "type", commandType).increment();
    }

    public void recordCommandFailure(String commandType) {
        commandFailureCounter.increment();
        // e.g., meterRegistry.counter("payment.command.processing.failure", "type", commandType).increment();
    }
}
