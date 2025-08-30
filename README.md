# Real-Time Payment System

This project is a comprehensive, event-driven payment processing system built on a microservices architecture. It is designed to be a scalable, resilient, and auditable platform for handling complex payment workflows. The system leverages modern technologies like Java 21, Spring Boot 3, Apache Kafka, and Docker to provide a robust foundation for financial applications.

At its core, the system is not just a simple connector to a payment gateway; it is a sophisticated platform for orchestrating the entire lifecycle of a transaction. It is ideal for businesses that operate at scale, serve multiple merchants, integrate with multiple payment providers, or have complex business logic that goes beyond a simple charge.

The architecture is built around the principles of Domain-Driven Design (DDD), Command Query Responsibility Segregation (CQRS), and Event Sourcing. This ensures a clear separation of concerns, a complete and immutable history of every transaction, and the ability to scale read and write operations independently.

## System Architecture

The system is composed of several loosely coupled microservices that communicate asynchronously via Apache Kafka. This event-driven approach ensures high resilience and scalability, as services can operate independently and are not blocked by the availability of others.
![System Architecture](docs/diagrams/system_architecture.png)                                                              │

### Core Concepts

*   **Event Sourcing**: The `payment-service` uses an event sourcing pattern to persist the state of a transaction. Instead of storing the current state, we store an immutable sequence of events that have occurred. This provides a full audit log and allows the state of an aggregate to be reconstructed at any point in time.
*   **CQRS (Command Query Responsibility Segregation)**: The system separates the responsibility of handling commands (write operations) from handling queries (read operations). This allows for independent scaling and optimization of each path.
*   **Saga Pattern**: The complex, multi-step process of a payment is orchestrated using a Saga. The `TransactionSagaOrchestrator` listens for events and dispatches new commands, ensuring that the entire workflow is completed or properly compensated in case of failure. 
     ![Payment Saga Flow](docs/diagrams/payment_saga_flow.png)                                                                  │ │
*   **Resilience and Fault Tolerance**: The services are built with resilience in mind, using patterns like Circuit Breaker, Retry, and Bulkhead (via Resilience4j) to prevent cascading failures and ensure graceful degradation.

### Further Reading on Architectural Decisions

For a deeper dive into the architectural decisions and patterns used in this project, please refer to the following documents:

*   **[Event Publishing Strategy](./EVENT_PUBLISHING_STRATEGY.md)**: An in-depth explanation of how we guarantee that events are only published after a database transaction is successfully committed, and our strategy for achieving at-least-once delivery.
*   **[Project Analysis and Refactoring Plan](./PROJECT_ANALYSIS_AND_REFACTORING_PLAN.md)**: A detailed analysis of the system's logical correctness, potential "God Objects," and a concrete plan for implementing production-ready features.
*   **[System Practicality Analysis](./SYSTEM_PRACTICALITY_ANALYSIS.md)**: A discussion on when this sophisticated architecture is a practical choice versus when a simpler approach would be more suitable.

## Development Workflow and Strategy

This section contains essential guides for testing, development workflows, and deployment strategies for the project.

*   **[Comprehensive Testing Plan](./testing_plan.md)**: A detailed, service-by-service strategy for implementing a robust suite of unit and integration tests. **This is a prerequisite for any new feature development.**
*   **[Integration (CI) and Deployment (CD) Strategy](./Integration (CI) and Deployment (CD).md)**: Outlines the recommended CI/CD pipeline setup, deployment strategy using Docker Swarm, and branching model for feature development.
*   **[Git Worktree Clarification](./Git worktree clarification.md)**: A practical guide and workflow for using `git worktree` to efficiently manage concurrent development tasks (e.g., feature work and hotfixes).
*   **[Mockito Spy Usage](./mockito_spy_usage.md)**: A detailed explanation of Mockito's `@Spy` annotation for advanced testing scenarios.

## Services

### 1. Transaction Service
*   **Purpose**: The public-facing entry point for all new transactions.
*   **Responsibilities**:
    *   Provides a REST API for creating new transactions.
    *   Performs initial validation on incoming requests.
    *   Creates a `TransactionCreatedEvent` and publishes it to Kafka.
*   **Technologies**: Spring Boot, Spring Web, Kafka, OpenAPI.

### 2. Payment Service
*   **Purpose**: The core of the system, responsible for orchestrating the entire payment lifecycle.
*   **Responsibilities**:
    *   Consumes `TransactionCreatedEvent` to initiate a new payment workflow.
    *   Implements the Saga pattern to manage the multi-step transaction process.
    *   Uses Event Sourcing to persist transaction state.
    *   Coordinates with the Fraud Detection Service.
    *   Publishes events to notify other services of the transaction's progress.
*   **Technologies**: Spring Boot, Kafka, Spring Data JDBC, PostgreSQL, Resilience4j.

### 3. Fraud Detection Service
*   **Purpose**: Provides fraud detection capabilities.
*   **Responsibilities**:
    *   Consumes `FraudCheckRequestedEvent`.
    *   Integrates with an external fraud detection API.
    *   Implements resilience patterns (Circuit Breaker, Retry) for external API calls.
    *   Publishes `FraudCheckCompletedEvent` with the result of the fraud check.
*   **Technologies**: Spring Boot, Kafka, Spring WebClient, Resilience4j.

### 4. Notification Service
*   **Purpose**: Handles all user-facing notifications.
*   **Responsibilities**:
    *   Consumes events like `TransactionCompletedEvent` and `PaymentFailedEvent`.
    *   Sends notifications to users via multiple channels (e.g., Email, SMS).
    *   Uses a factory and strategy pattern to manage different notification types.
*   **Technologies**: Spring Boot, Kafka, Spring Mail.

## Getting Started

The entire system is containerized and can be run easily with Docker Compose.

### Prerequisites
*   Docker
*   Docker Compose
*   Java 21
*   Maven

### Running the System
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/Real-Time-Payment-System.git
    cd Real-Time-Payment-System
    ```

2.  **Build the project**:
    ```bash
    mvn clean install
    ```

3.  **Start the services**:
    ```bash
    docker-compose up -d
    ```

This will start all the microservices, along with the necessary infrastructure (Kafka, Zookeeper, PostgreSQL).

## API Documentation

The `transaction-service` provides an OpenAPI (Swagger) specification for its API. Once the services are running, you can access the Swagger UI at:

[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

From here, you can view the available endpoints and execute API requests to create new transactions.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE.md) file for details.

## Author

*   **Nevil Maloba**