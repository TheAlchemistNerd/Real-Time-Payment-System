# Real-Time Payment System

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

This project is a comprehensive, event-driven payment processing system built on a microservices architecture. It is designed to be a scalable, resilient, and auditable platform for handling complex payment workflows. The system leverages modern technologies like Java 21, Spring Boot 3, Apache Kafka, and Docker to provide a robust foundation for financial applications.

At its core, the system is not just a simple connector to a payment gateway; it is a sophisticated platform for orchestrating the entire lifecycle of a transaction. It is ideal for businesses that operate at scale, serve multiple merchants, integrate with multiple payment providers, or have complex business logic that goes beyond a simple charge.

The architecture is built around the principles of Domain-Driven Design (DDD), Command Query Responsibility Segregation (CQRS), and Event Sourcing. This ensures a clear separation of concerns, a complete and immutable history of every transaction, and the ability to scale read and write operations independently.

## Table of Contents

- [Real-Time Payment System](#real-time-payment-system)
  - [Table of Contents](#table-of-contents)
  - [System Architecture](#system-architecture)
    - [Core Concepts](#core-concepts)
    - [Architectural Deep Dive](#architectural-deep-dive)
  - [Services](#services)
    - [1. Transaction Service](#1-transaction-service)
    - [2. Payment Service](#2-payment-service)
    - [3. Fraud Detection Service](#3-fraud-detection-service)
    - [4. Notification Service](#4-notification-service)
  - [Key Design Patterns and Strategies](#key-design-patterns-and-strategies)
    - [Payment Saga Flow: Orchestration vs. Choreography](#payment-saga-flow-orchestration-vs-choreography)
    - [Reliable Event Publishing Strategy](#reliable-event-publishing-strategy)
    - [Security and Encryption Practices](#security-and-encryption-practices)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Running the System](#running-the-system)
  - [API Documentation](#api-documentation)
  - [Development and Deployment](#development-and-deployment)
    - [Comprehensive Testing Plan](#comprehensive-testing-plan)
    - [CI/CD and Branching Strategy](#cicd-and-branching-strategy)
  - [System Practicality Analysis](#system-practicality-analysis)
  - [License](#license)
  - [Author](#author)

## System Architecture

The system is composed of several loosely coupled microservices that communicate asynchronously via Apache Kafka. This event-driven approach ensures high resilience and scalability, as services can operate independently and are not blocked by the availability of others.

![System Architecture](docs/images/system_architecture.png)

### Core Concepts

*   **Event Sourcing**: The `payment-service` uses an event sourcing pattern to persist the state of a transaction. Instead of storing the current state, we store an immutable sequence of events that have occurred. This provides a full audit log and allows the state of an aggregate to be reconstructed at any point in time.
*   **CQRS (Command Query Responsibility Segregation)**: The system separates the responsibility of handling commands (write operations) from handling queries (read operations). This allows for independent scaling and optimization of each path.
*   **Saga Pattern**: The complex, multi-step process of a payment is orchestrated using a Saga. The `TransactionSagaOrchestrator` listens for events and dispatches new commands, ensuring that the entire workflow is completed or properly compensated in case of failure.
    ![Payment Saga Flow](docs/images/payment_saga_flow.png)
*   **Resilience and Fault Tolerance**: The services are built with resilience in mind, using patterns like Circuit Breaker, Retry, and Bulkhead (via Resilience4j) to prevent cascading failures and ensure graceful degradation.

### Architectural Deep Dive

For a deeper understanding of the architectural decisions and patterns used in this project, please refer to the detailed sections later in this document.

## Services

### 1. Transaction Service

*   **Purpose**: The public-facing entry point for all new transactions.
*   **Responsibilities**:
    *   Provides a REST API for creating new transactions.
    *   Performs initial validation on incoming requests.
    *   Creates a `TransactionCreatedEvent` and publishes it to Kafka.
*   **Technologies**: Spring Boot, Spring Web, Kafka, OpenAPI, Resilience4j (RateLimiter).

### 2. Payment Service

*   **Purpose**: The core of the system, responsible for orchestrating the entire payment lifecycle.
*   **Responsibilities**:
    *   Consumes `TransactionCreatedEvent` to initiate a new payment workflow.
    *   Implements the Saga pattern to manage the multi-step transaction process.
    *   Uses Event Sourcing with a JDBC-based Event Store to persist transaction state.
    *   Coordinates with the Fraud Detection Service.
    *   Publishes events to notify other services of the transaction's progress.
*   **Technologies**: Spring Boot, Kafka, Spring Data JDBC, PostgreSQL, Resilience4j.

### 3. Fraud Detection Service

*   **Purpose**: Provides fraud detection capabilities by integrating with external APIs.
*   **Responsibilities**:
    *   Consumes `FraudCheckRequestedEvent`.
    *   Integrates with an external fraud detection API (e.g., Sift Science).
    *   Implements resilience patterns (Circuit Breaker, Retry, TimeLimiter) for external API calls.
    *   Publishes `FraudCheckCompletedEvent` with the result of the fraud check.
*   **Technologies**: Spring Boot, Kafka, Spring WebClient, Resilience4j.

### 4. Notification Service

*   **Purpose**: Handles all user-facing notifications.
*   **Responsibilities**:
    *   Consumes events like `TransactionCompletedEvent` and `PaymentFailedEvent`.
    *   Sends notifications to users via multiple channels (Email, SMS, Webhook).
    *   Uses a Factory and Strategy pattern to manage different notification types.
    *   Employs an async thread pool for non-blocking I/O operations.
*   **Technologies**: Spring Boot, Kafka, Spring Mail.

## Key Design Patterns and Strategies

### Payment Saga Flow: Orchestration vs. Choreography

This system uses an **Orchestration-based Saga pattern**, with the `TransactionSagaOrchestrator` in the `payment-service` acting as the central coordinator.

**Why Orchestration?**
For a business-critical workflow like payments, orchestration provides:
*   **High Control & Visibility**: A single point to monitor and debug the entire payment flow, making it easy to answer "Where is my money?".
*   **Explicit Process Definition**: The business process is clearly defined in the orchestrator's code, making it easier to understand and modify.
*   **Centralized Error Handling**: The orchestrator is the natural place to implement robust, process-wide error handling and retry logic.

**Compensating Actions:**
The current flow is "forward-only," but the orchestration pattern provides the ideal structure to add compensating actions (rollbacks) in the future if business requirements change.

### Reliable Event Publishing Strategy

To prevent consumers from receiving events for database transactions that were never committed, the system uses a critical pattern:

1.  **`TransactionSynchronizationManager.registerSynchronization`**: Events are not published immediately. Instead, a callback is registered with Spring's transaction manager.
2.  **`afterCommit()`**: The event publishing logic is placed within the `afterCommit()` hook. This **guarantees** that events are only sent to Kafka if the database transaction succeeds.

This prioritizes data consistency. For an even higher guarantee of delivery, the next step would be to implement the **Transactional Outbox Pattern**, which is detailed in the project analysis documents.

### Security and Encryption Practices

#### Data in Transit: JWE for External Communication

For securely transmitting sensitive data to external parties like payment gateways, the **JSON Web Encryption (JWE)** standard is recommended.
*   **Library**: **Nimbus JOSE + JWT** is the suggested implementation due to its standards compliance and support for strong cryptographic algorithms.
*   **Best Practices**:
    *   **Key Management**: Never hardcode keys. Use a secure key vault (e.g., HashiCorp Vault, AWS KMS).
    *   **Algorithm Choice**: Use strong algorithms like `RSA-OAEP-256` for key encryption and `A256GCM` for content encryption.

#### Data at Rest: Internal Encryption Utility

The `common` module contains an `EncryptionUtil` for internal, symmetric encryption.
*   **Critical Flaw Identified**: The utility uses `AES/ECB/PKCS5Padding`. **ECB mode is insecure** and vulnerable to pattern analysis.
*   **Recommendation**:
    1.  **Do Not Remove**: This utility serves a different purpose than JWE.
    2.  **Immediate Refactoring Required**: The utility **must be refactored** to use a secure mode like `AES/GCM/NoPadding` with a unique Initialization Vector (IV) for each encryption operation.

## Getting Started

The entire system is containerized and can be run easily with Docker Compose.

### Prerequisites

*   Docker & Docker Compose
*   Java 21
*   Maven

### Running the System

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/Real-Time-Payment-System.git
    cd Real-Time-Payment-System
    ```

2.  **Build the project**:
    This command will compile all modules and build the necessary JAR files.
    ```bash
    mvn clean install
    ```

3.  **Start the services**:
    This will start all the microservices, along with the necessary infrastructure (Kafka, Zookeeper, PostgreSQL, Prometheus, Grafana).
    ```bash
    docker-compose up -d
    ```

## API Documentation

The `transaction-service` provides an OpenAPI (Swagger) specification for its API. Once the services are running, you can access the Swagger UI at:

[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

From here, you can view the available endpoints and execute API requests to create new transactions.

## Development and Deployment

### Comprehensive Testing Plan

A test-first approach is essential for this project. A detailed, service-by-service testing plan has been formulated to create a robust safety net before any new features (like third-party integrations) are added. The strategy is guided by the Testing Pyramid, emphasizing a strong base of unit tests, followed by integration tests using the **Testcontainers** library to validate interactions with real infrastructure.

### CI/CD and Branching Strategy

*   **Continuous Integration**: A CI/CD pipeline (e.g., using GitHub Actions) should be set up to run `mvn clean verify` on every push and pull request, acting as a quality gate.
*   **Deployment**: A pragmatic approach is to deploy first to **Docker Swarm** for its simplicity and speed, with a plan to migrate to Kubernetes on a separate branch later.
*   **Branching**: All new features and integrations **must** be developed on isolated feature branches (e.g., `feature/stripe-integration`). The `main` branch should always be stable and deployable.

## System Practicality Analysis

This system is a sophisticated payment platform, not a simple connector. It is highly practical for businesses with complex needs:
*   **Platform Businesses** (e.g., Shopify, Etsy) serving many merchants.
*   **High Volume** of transactions requiring horizontal scalability.
*   **Multiple Payment Gateways** with intelligent routing.
*   **Complex Business Logic** (fraud checks, inventory, discounts) orchestrated by the Saga.
*   **High Resilience and Auditing** requirements.

For a simple e-commerce store that only needs to connect to Stripe, this architecture would be overkill.

## License

This project is licensed under the MIT License. See the [LICENSE.md](LICENSE.md) file for details.

## Author

*   **Nevil Maloba**
