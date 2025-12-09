package com.example.ratelimiter.domain.entity;

import com.example.ratelimiter.domain.enums.IdentifierType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Entity
@Table(name = "rate_limit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(nullable = false)
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 50)
    private IdentifierType identifierType;

    @Column(nullable = false)
    private boolean allowed;

    @Column(nullable = false)
    private Integer remaining;

    @Column(name = "limit_value", nullable = false)
    private Integer limitValue;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    private String resource;

    @Column(name = "event_time", nullable = false)
    @Builder.Default
    private OffsetDateTime eventTime = OffsetDateTime.now();

    @Column(name = "partition_key", nullable = false, length = 7)
    private String partitionKey;

    @PrePersist
    @PreUpdate
    private void generatePartitionKey() {
        if (eventTime != null) {
            partitionKey = eventTime.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        } else {
            partitionKey = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
    }
}
