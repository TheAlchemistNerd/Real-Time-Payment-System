package com.paymentprocessor.payment.repository;

import com.paymentprocessor.common.exception.TransactionNotFoundException;
import com.paymentprocessor.payment.aggregate.TransactionAggregate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Slf4j
@Repository
public class TransactionAggregateRepository {

    private final EventStore eventStore;

    public TransactionAggregateRepository(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public void save(TransactionAggregate aggregate) {
        log.info("Saving aggregate: {}", aggregate.getTransactionId());

        var uncommittedEvents = aggregate.getUncommittedEvents();
        if(uncommittedEvents.isEmpty()) {
            log.debug("No uncommitted events to save for aggregate: {}", aggregate.getTransactionId());
            return;
        }

        long expectedVersion = aggregate.getVersion() - uncommittedEvents.size();
        eventStore.saveEvents(aggregate.getTransactionId(), uncommittedEvents, expectedVersion);
        aggregate.clearUncommittedEvents();

        log.info("Successfully saved aggregate: {}", aggregate.getTransactionId());
    }

    public TransactionAggregate findById(String transactionId) {
        log.debug("Loading aggregate: {}", transactionId);
        var events = eventStore.getEventsForAggregate(transactionId);
        if (events.isEmpty()) {
            log.warn("No events found for aggregate: {}", transactionId);
            throw new TransactionNotFoundException(transactionId);
        }

        var aggregate = new TransactionAggregate(transactionId);
        aggregate.loadFromHistory(events);

        log.debug("Successfully loaded aggregate: {} with {} events", transactionId, events.size());
        return aggregate;
    }

    public Optional<TransactionAggregate> findByIdOptional(String transactionId) {
        try {
            return Optional.of(findById(transactionId));
        } catch (TransactionNotFoundException e) {
            return Optional.empty();
        }
    }
}
