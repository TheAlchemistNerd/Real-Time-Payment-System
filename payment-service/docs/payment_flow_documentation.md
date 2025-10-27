# Payment Service: Architecture and Flow Documentation

## Introduction

This document provides a comprehensive overview of the Real-Time Payment System's `payment-service`. It details the current architectural design, the end-to-end flow of processing a payment, and a strategic roadmap for future enhancements. The primary goal of this service is to provide a robust, scalable, and extensible system for handling financial transactions.

The architecture is heavily influenced by principles from Command Query Responsibility Segregation (CQRS) and Event Sourcing. This approach allows for a clear separation of concerns, enhanced resilience, and a high degree of auditability. At the heart of the service is the `TransactionAggregate`, which acts as the single source of truth for the state of any given transaction.

This document is divided into two main parts:
1.  **Current Architecture**: A deep dive into the existing components and their interactions during a standard payment flow.
2.  **Future Directions**: A roadmap outlining proposed enhancements to the `PaymentGatewayClient` interface to support more complex business requirements and improve system robustness.

---

## Part 1: Current Architecture - A Decoupled and Resilient Approach

The current architecture is designed to isolate business logic from external dependencies, ensuring that the core rules of a transaction are managed consistently while allowing for flexibility in how external services, like payment gateways, are integrated.

### Core Components

The system is composed of several key components that work in concert to process payments.

#### 1. `TransactionCommandService`: The Orchestrator

The `TransactionCommandService` acts as the primary entry point and orchestrator for any command that intends to modify the state of a transaction. It is not responsible for *how* the state changes, but rather for coordinating the steps involved in the process.

Its key responsibilities include:
-   Receiving commands (e.g., `ProcessPaymentCommand`).
-   Loading the relevant `TransactionAggregate` from the repository.
-   Initiating state changes by calling the appropriate `handle` method on the aggregate.
-   Coordinating with external services, such as the `PaymentGatewayFactory`, to perform I/O operations.
-   Applying the results of external operations back to the aggregate.
-   Persisting the aggregate's new state and publishing any resulting events.

```java
// Example from TransactionCommandService.java
@Transactional
public void handle(ProcessPaymentCommand command) {
    // 1. Load the aggregate
    var aggregate = repository.findById(command.transactionId());

    // 2. Defer state change to the aggregate
    aggregate.handle(command); // Applies PaymentProcessingStartedEvent

    // 3. Orchestrate external call
    var gateway = paymentGatewayFactory.getGateway(command.paymentGateway());
    var paymentRequest = new com.paymentprocessor.payment.dto.PaymentRequest(...);

    gateway.processPayment(paymentRequest)
        .doOnSuccess(response -> {
            // 4. Apply the outcome back to the aggregate
            if (response.success()) {
                var processedEvent = new PaymentProcessedEvent(...);
                aggregate.apply(processedEvent);
            } else {
                var failedEvent = new PaymentFailedEvent(...);
                aggregate.apply(failedEvent);
            }
            // 5. Persist and publish
            repository.save(aggregate);
            publishEventsAfterCommit(aggregate);
        })
        // ... error handling
}
```

#### 2. `TransactionAggregate`: The State Machine

The `TransactionAggregate` is the guardian of a transaction's state and consistency. It encapsulates all the business logic and rules related to a single transaction. It operates as a state machine, transitioning from one status to another (e.g., `PENDING` -> `FRAUD_CHECK_PASSED` -> `PAYMENT_PROCESSING` -> `COMPLETED`/`FAILED`) only in response to valid events.

Crucially, the aggregate **never performs I/O operations**. It does not know about databases, message queues, or external APIs. Its world is confined to its own state. This design choice is critical for testability, consistency, and adherence to the Single Responsibility Principle.

State changes are applied by handling commands and generating events. Each event represents a fact—something that has already happened. The aggregate's state is derived by applying these events in order.

```java
// Example from TransactionAggregate.java
public void handle(ProcessPaymentCommand command) {
    // Rule: A payment can only be processed if fraud check has passed.
    validateTransactionStatus(TransactionStatus.FRAUD_CHECK_PASSED);

    // Create an event to signify the start of processing.
    var startedEvent = new PaymentProcessingStartedEvent(
            command.transactionId(),
            command.amount(),
            this.paymentMethod,
            command.paymentGateway()
    );
    apply(startedEvent); // This changes the status to PAYMENT_PROCESSING
}

public void on(PaymentProcessingStartedEvent event) {
    this.status = TransactionStatus.PAYMENT_PROCESSING;
    this.version++;
}
```

#### 3. `PaymentGatewayClient` Interface: The Abstraction Layer

This interface is the cornerstone of our external communication strategy. It defines a standard contract for interacting with any payment gateway, completely decoupling our core business logic from the specific implementation details of providers like Stripe or PayPal.

```java
public interface PaymentGatewayClient {
    Mono<PaymentResponse> processPayment(PaymentRequest request);
    Mono<RefundResponse> refundPayment(RefundRequest request);
    Mono<String> getPaymentStatus(String transactionId);
}
```
This abstraction allows us to:
-   **Add new gateways** with minimal changes to the core application.
-   **Mock gateways** easily in tests.
-   **Isolate provider-specific logic** into dedicated client classes.

#### 4. `PaymentGatewayFactory`: The Intelligent Router

The `PaymentGatewayFactory` is a simple but powerful component responsible for selecting the correct `PaymentGatewayClient` implementation at runtime based on a given identifier (e.g., "stripe" or "paypal"). It uses Spring's dependency injection to get access to all available gateway clients and provides a single method to retrieve the appropriate one.

This enables dynamic routing, which could be expanded to include logic based on currency, region, or transaction amount.

### The End-to-End Payment Flow

Here is a step-by-step breakdown of what happens when a `ProcessPaymentCommand` is handled:

1.  **Command Arrival**: The `TransactionCommandService` receives the `ProcessPaymentCommand`, which contains the transaction ID, amount, and chosen payment gateway.
2.  **Aggregate Loading**: The service retrieves the `TransactionAggregate` instance for the given transaction ID from the repository.
3.  **Initiate Processing State**: The service calls `aggregate.handle(command)`. The aggregate validates that its current status is `FRAUD_CHECK_PASSED`. If valid, it creates and applies a `PaymentProcessingStartedEvent`, which changes its internal status to `PAYMENT_PROCESSING`.
4.  **Gateway Selection**: The `TransactionCommandService` uses the `PaymentGatewayFactory` to get the concrete client for the requested gateway (e.g., an instance of `StripeClient`).
5.  **External API Call**: The service constructs a `PaymentRequest` DTO and calls the `processPayment` method on the selected gateway client. This is an asynchronous, non-blocking network call.
6.  **Handling the Response**:
    *   **On Success**: If the payment is successful, the service creates a `PaymentProcessedEvent` containing the gateway's transaction ID. It then calls `aggregate.apply(processedEvent)`. The aggregate's `on(PaymentProcessedEvent)` method updates its status to `COMPLETED`.
    *   **On Failure**: If the payment fails, the service creates a `PaymentFailedEvent` with the reason for failure. It calls `aggregate.apply(failedEvent)`, and the aggregate's status is updated to `FAILED`.
7.  **Persistence and Event Publication**: The `TransactionCommandService` saves the updated aggregate to the database. After the database transaction is successfully committed, it publishes all the new, uncommitted events (like `PaymentProcessingStartedEvent` and `PaymentProcessedEvent`) to a message queue for other services to consume.

---

## Part 2: Future Directions & Enhancing the Payment Gateway

The current implementation provides a solid foundation for direct payment processing. However, real-world payment scenarios are often more complex. The following sections outline a roadmap for extending the `PaymentGatewayClient` interface to support a wider range of business needs and to build a more resilient system.

### Section 2.1: Implementing the Authorize and Capture Flow

The most critical enhancement for many business models is the separation of authorization and capture. This two-step process is standard for e-commerce, rentals, and service-based industries.

-   **Authorization**: A hold is placed on the customer's funds for a specific amount, but the funds are not yet transferred. This guarantees that the funds are available.
-   **Capture**: The previously authorized funds are actually transferred from the customer to the merchant.

**Proposed Interface Additions:**

```java
public interface PaymentGatewayClient {
    // ... existing methods
    Mono<AuthorizationResponse> authorizePayment(AuthorizationRequest request);
    Mono<CaptureResponse> capturePayment(CaptureRequest request);
    Mono<VoidResponse> voidPayment(VoidRequest request);
}
```

-   **`authorizePayment(AuthorizationRequest request)`**: This method would be used to reserve funds. The `TransactionAggregate` would transition to a new `AUTHORIZED` status. This is essential for scenarios like online shopping (authorize on checkout, capture on shipment) or hotel bookings (authorize on reservation, capture on check-in).

-   **`capturePayment(CaptureRequest request)`**: This method finalizes the charge. It can be for the full authorized amount or a partial amount. This would transition the aggregate from `AUTHORIZED` to `COMPLETED`.

-   **`voidPayment(VoidRequest request)`**: This method cancels an authorization *before* it has been captured. This is a critical operation for handling order cancellations, as it avoids costly refund fees and is much faster for the customer. This would transition the aggregate from `AUTHORIZED` to `CANCELLED` or `VOIDED`.

### Section 2.2: Customer and Payment Method Tokenization

To enhance security and improve the user experience for returning customers, we should avoid handling raw payment details directly. Tokenization involves storing customer payment information securely with the payment gateway and using a non-sensitive token to reference it for future transactions.

**Proposed Interface Additions:**

```java
public interface PaymentGatewayClient {
    // ... existing methods
    Mono<CustomerResponse> createCustomer(CreateCustomerRequest request);
    Mono<PaymentMethodResponse> addPaymentMethod(AddPaymentMethodRequest request);
}
```

-   **`createCustomer(...)`**: Creates a customer object within the gateway's system, returning a unique customer ID. This profile can then be used to manage payment methods and view transaction history.

-   **`addPaymentMethod(...)`**: Securely associates a payment method (like a credit card) with a customer profile and returns a payment method token (e.g., `pm_123xyz`). Subsequent payment requests can use this token instead of raw card numbers, drastically reducing the PCI compliance scope of our application. This is the foundation for "one-click" checkout and subscription billing.

### Section 2.3: Enhanced Reporting and Reconciliation

While `getPaymentStatus` is useful for a single transaction, financial operations require the ability to query and reconcile large sets of transactions.

**Proposed Interface Addition:**

```java
public interface PaymentGatewayClient {
    // ... existing methods
    Flux<Transaction> listTransactions(ListTransactionsRequest request);
}
```

-   **`listTransactions(...)`**: This method would allow for fetching transactions based on filters like date range, customer ID, or status. Using a reactive `Flux` return type is ideal, as it allows for streaming a large number of results without overwhelming the service's memory. This is essential for building internal dashboards, performing financial audits, and reconciling accounts.

### Section 2.4: Critical Implementation Considerations for Robustness

Beyond new features, the implementation of the client should incorporate patterns that ensure reliability and resilience.

#### Idempotency

Payment requests must be idempotent. This means that if the same request is sent multiple times (e.g., due to a network timeout where the client isn't sure if the first request succeeded), it is only processed once. This is typically achieved by including a unique `Idempotency-Key` in the request header or body. The DTOs for all state-changing operations (`processPayment`, `refundPayment`, `authorizePayment`, etc.) should be updated to include this key.

#### Richer Error Handling

The current `PaymentResponse` with a simple `boolean success` and `String message` is a good start, but it can be improved. A more robust design would involve a dedicated `ErrorResponse` object containing:
-   **Gateway-specific error codes**: e.g., `card_declined`, `insufficient_funds`.
-   **Error types**: e.g., `API_ERROR`, `CARD_ERROR`, `VALIDATION_ERROR`.
-   **A detailed, developer-friendly message**.

This allows the `TransactionCommandService` to make more intelligent decisions based on the type of error. For example, a `CARD_ERROR` is a final failure, whereas an `API_ERROR` might be retryable.

#### Webhooks

Finally, a complete payment system cannot rely solely on synchronous API calls. Many payment events happen asynchronously after the initial transaction. Examples include:
-   **Chargebacks**: A customer disputes a charge with their bank.
-   **Settlement Confirmations**: Notification that funds have actually been deposited.
-   **Subscription Renewal Failures**.

Our system needs a dedicated endpoint to receive and process webhooks from the payment gateway. These webhooks would be translated into commands that are then processed by the `TransactionCommandService`, ensuring that the `TransactionAggregate` always reflects the absolute latest state of the transaction.

## Conclusion

The `payment-service` is built on a solid architectural foundation that emphasizes separation of concerns, testability, and resilience. The current implementation of the payment flow is robust for direct, immediate transactions. The future roadmap outlined in this document provides a clear path to evolving the system into a comprehensive and highly versatile payment processing platform capable of supporting a wide array of complex business requirements.
