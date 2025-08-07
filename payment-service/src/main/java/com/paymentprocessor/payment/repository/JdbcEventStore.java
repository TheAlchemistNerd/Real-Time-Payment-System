package com.paymentprocessor.payment.repository;

import com.paymentprocessor.common.event.BaseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class JdbcEventStore implements EventStore {

    @Override
    @Transactional
    public void saveEvents(String aggregateId, List<BaseEvent> events, long expectedVersion) {

    }

    @Override
    public List<BaseEvent> getEventsForAggregate(String aggregateId) {
        return List.of();
    }

    @Override
    public Optional<Long> getLastVersionForAggregate(String aggregateId) {
        return Optional.empty();
    }
}
