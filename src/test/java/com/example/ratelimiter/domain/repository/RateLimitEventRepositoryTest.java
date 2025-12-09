package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.RateLimitEvent;
import com.example.ratelimiter.domain.enums.IdentifierType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("RateLimitEventRepository Tests")
class RateLimitEventRepositoryTest {

    @Autowired
    private RateLimitEventRepository eventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Should find events by policy ID with pagination")
    void findByPolicyId_eventsExist_returnsPaginatedResults() {
        // Given
        UUID policyId = UUID.randomUUID();
        createAndSaveEvent(policyId, "user1", true);
        createAndSaveEvent(policyId, "user2", false);
        createAndSaveEvent(policyId, "user3", true);

        // When
        Page<RateLimitEvent> page = eventRepository.findByPolicyId(policyId, PageRequest.of(0, 2));

        // Then
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should find events by identifier")
    void findByIdentifier_eventsExist_returnsIdentifierEvents() {
        // Given
        String identifier = "user123";
        createAndSaveEvent(UUID.randomUUID(), identifier, true);
        createAndSaveEvent(UUID.randomUUID(), identifier, false);
        createAndSaveEvent(UUID.randomUUID(), "other_user", true);

        // When
        Page<RateLimitEvent> page = eventRepository.findByIdentifier(identifier, PageRequest.of(0, 10));

        // Then
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allMatch(e -> e.getIdentifier().equals(identifier));
    }

    @Test
    @DisplayName("Should find events by identifier type")
    void findByIdentifierType_eventsExist_returnsTypedEvents() {
        // Given
        createAndSaveEventWithType(UUID.randomUUID(), "user1", IdentifierType.USER_ID, true);
        createAndSaveEventWithType(UUID.randomUUID(), "key1", IdentifierType.API_KEY, true);
        createAndSaveEventWithType(UUID.randomUUID(), "user2", IdentifierType.USER_ID, false);

        // When
        Page<RateLimitEvent> userEvents = eventRepository.findByIdentifierType(
                IdentifierType.USER_ID, PageRequest.of(0, 10));

        // Then
        assertThat(userEvents.getContent()).hasSize(2);
        assertThat(userEvents.getContent()).allMatch(e -> e.getIdentifierType() == IdentifierType.USER_ID);
    }

    @Test
    @DisplayName("Should find events by time range")
    void findByEventTimeBetween_eventsExist_returnsEventsInRange() {
        // Given
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime oneHourAgo = now.minusHours(1);
        OffsetDateTime twoHoursAgo = now.minusHours(2);

        createAndSaveEventWithTime(UUID.randomUUID(), "user1", twoHoursAgo);
        createAndSaveEventWithTime(UUID.randomUUID(), "user2", oneHourAgo);
        createAndSaveEventWithTime(UUID.randomUUID(), "user3", now);

        // When
        List<RateLimitEvent> events = eventRepository.findByEventTimeBetween(
                oneHourAgo.minusMinutes(1), now.plusMinutes(1));

        // Then
        assertThat(events).hasSize(2);
    }

    @Test
    @DisplayName("Should count events by policy ID and time range")
    void countByPolicyIdAndTimeBetween_eventsExist_returnsCount() {
        // Given
        UUID policyId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime oneHourAgo = now.minusHours(1);

        createAndSaveEventWithTime(policyId, "user1", oneHourAgo.plusMinutes(10));
        createAndSaveEventWithTime(policyId, "user2", oneHourAgo.plusMinutes(20));
        createAndSaveEventWithTime(policyId, "user3", oneHourAgo.plusMinutes(30));

        // When
        long count = eventRepository.countByPolicyIdAndTimeBetween(policyId, oneHourAgo, now);

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Should count allowed events by policy ID")
    void countAllowedByPolicyId_eventsExist_returnsAllowedCount() {
        // Given
        UUID policyId = UUID.randomUUID();
        createAndSaveEvent(policyId, "user1", true);
        createAndSaveEvent(policyId, "user2", true);
        createAndSaveEvent(policyId, "user3", false);

        // When
        long allowedCount = eventRepository.countAllowedByPolicyId(policyId);

        // Then
        assertThat(allowedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Should count denied events by policy ID")
    void countDeniedByPolicyId_eventsExist_returnsDeniedCount() {
        // Given
        UUID policyId = UUID.randomUUID();
        createAndSaveEvent(policyId, "user1", true);
        createAndSaveEvent(policyId, "user2", false);
        createAndSaveEvent(policyId, "user3", false);

        // When
        long deniedCount = eventRepository.countDeniedByPolicyId(policyId);

        // Then
        assertThat(deniedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Should count events grouped by identifier type")
    void countByPolicyIdGroupedByIdentifierType_eventsExist_returnsGroupedCounts() {
        // Given
        UUID policyId = UUID.randomUUID();
        createAndSaveEventWithType(policyId, "user1", IdentifierType.USER_ID, true);
        createAndSaveEventWithType(policyId, "user2", IdentifierType.USER_ID, true);
        createAndSaveEventWithType(policyId, "key1", IdentifierType.API_KEY, true);

        // When
        List<Object[]> groupedCounts = eventRepository.countByPolicyIdGroupedByIdentifierType(policyId);

        // Then
        assertThat(groupedCounts).hasSize(2);
        // Find USER_ID group
        Optional<Object[]> userIdGroup = groupedCounts.stream()
                .filter(arr -> arr[0] == IdentifierType.USER_ID)
                .findFirst();
        assertThat(userIdGroup).isPresent();
        assertThat((Long) userIdGroup.get()[1]).isEqualTo(2L);
    }

    // Helper methods
    private void createAndSaveEvent(UUID policyId, String identifier, boolean allowed) {
        RateLimitEvent event = RateLimitEvent.builder()
                .policyId(policyId)
                .identifier(identifier)
                .identifierType(IdentifierType.USER_ID)
                .allowed(allowed)
                .remaining(50)
                .limitValue(100)
                .eventTime(OffsetDateTime.now())
                .build();
        entityManager.persist(event);
    }

    private void createAndSaveEventWithType(UUID policyId, String identifier,
                                           IdentifierType type, boolean allowed) {
        RateLimitEvent event = RateLimitEvent.builder()
                .policyId(policyId)
                .identifier(identifier)
                .identifierType(type)
                .allowed(allowed)
                .remaining(50)
                .limitValue(100)
                .eventTime(OffsetDateTime.now())
                .build();
        entityManager.persist(event);
    }

    private void createAndSaveEventWithTime(UUID policyId, String identifier, OffsetDateTime time) {
        RateLimitEvent event = RateLimitEvent.builder()
                .policyId(policyId)
                .identifier(identifier)
                .identifierType(IdentifierType.USER_ID)
                .allowed(true)
                .remaining(50)
                .limitValue(100)
                .eventTime(time)
                .build();
        entityManager.persist(event);
    }
}
