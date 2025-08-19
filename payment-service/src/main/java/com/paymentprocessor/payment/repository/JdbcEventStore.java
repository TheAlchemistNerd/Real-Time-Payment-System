package com.paymentprocessor.payment.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentprocessor.common.event.BaseEvent;
import com.paymentprocessor.common.exception.PaymentProcessingFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class JdbcEventStore implements EventStore {

    private static final String SAVE_EVENT_SQL = """
            INSERT INTO domain_events (aggregate_identifier, sequence_number, event_type, payload, timestamp)
            VALUES (?, ?, ?, ?::jsonb, ?)
            """;

    private static final String LOAD_EVENTS_SQL = """
            SELECT event_type, payload, timestamp
            FROM domain_events
            WHERE aggregate_identifier = ?
            ORDER BY sequence_number ASC
            """;

    private static final String GET_LAST_VERSION_SQL = """
            SELECT MAX(sequence_number)
            FROM domain_events
            WHERE aggregate_identifier = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcEventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void saveEvents(String aggregateId, List<BaseEvent> events, long expectedVersion) {
        log.info("Saving {} events for aggregate: {}", events.size(), aggregateId);

        // Check Optimistic locking
        var currentVersion = getLastVersionForAggregate(aggregateId);
        if(currentVersion.isPresent() && !currentVersion.get().equals(expectedVersion)) {
            throw new OptimisticLockingFailureException(
                    "Aggregate %s has been modified. Expected version: %d Current version: %d"
                            .formatted(aggregateId, expectedVersion, currentVersion.get()));
        }

        long sequenceNumber = expectedVersion;
        for (var event : events) {
            sequenceNumber++;
            try {
                var payload = objectMapper.writeValueAsString(event);
                jdbcTemplate.update(SAVE_EVENT_SQL, aggregateId, sequenceNumber,
                        event.eventType(), payload, event.timestamp());
                log.debug("Saved event {} for aggregate {} with sequence {}",
                        event.eventType(), aggregateId, sequenceNumber);

            } catch (Exception e) {
                log.error("Failed to save event for aggregate: {}", aggregateId, e);
                throw new PaymentProcessingFailedException("Failed to save event", e);
            }
        }

        log.info("Successfully saved {} events for aggregate: {}", events.size(), aggregateId);
    }

    @Override
    public List<BaseEvent> getEventsForAggregate(String aggregateId) {
        log.debug("Loading events for aggregate: {}", aggregateId);

        var events = jdbcTemplate.query(LOAD_EVENTS_SQL,
                this::mapRowToEvent, aggregateId);

        log.debug("Loaded {} events for aggregate: {}", events.size(), aggregateId);
        return events;
    }


    @Override
    public Optional<Long> getLastVersionForAggregate(String aggregateId) {
        var version = jdbcTemplate.queryForObject(GET_LAST_VERSION_SQL,
                Long.class, aggregateId);
        return Optional.ofNullable(version);
    }

    private BaseEvent mapRowToEvent(ResultSet rs, int rowNum) throws SQLException {
        try {
            var eventType = rs.getString("event_type");
            var payload = rs.getString("payload");
            return objectMapper.readValue(payload, BaseEvent.class);
        } catch (Exception e) {
            log.error("Failed to deserialize event from database", e);
            throw new PaymentProcessingFailedException("Failed to deserialize event", e);
        }
    }
}
