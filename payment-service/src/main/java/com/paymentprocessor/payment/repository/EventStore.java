package com.paymentprocessor.payment.repository;

import com.paymentprocessor.common.event.BaseEvent;

import java.util.List;
import java.util.Optional;

public interface EventStore {
    void saveEvents(String aggregateId, List<BaseEvent> events,
                    long expectedVersion);
    List<BaseEvent> getEventsForAggregate(String aggregateId);
    Optional<Long> getLastVersionForAggregate(String aggregateId);
}
