package com.example.ratelimiter.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private Policy policy;

    @Column(name = "threshold_percentage", nullable = false)
    private Integer thresholdPercentage;

    @Column(name = "window_seconds", nullable = false)
    @Builder.Default
    private Integer windowSeconds = 60;

    @Column(name = "cooldown_seconds", nullable = false)
    @Builder.Default
    private Integer cooldownSeconds = 300;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "last_triggered_at")
    private OffsetDateTime lastTriggeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validateConstraints() {
        if (thresholdPercentage == null || thresholdPercentage < 1 || thresholdPercentage > 100) {
            throw new IllegalStateException("Threshold percentage must be between 1 and 100");
        }
        if (windowSeconds == null || windowSeconds < 1) {
            throw new IllegalStateException("Window seconds must be at least 1");
        }
        if (cooldownSeconds == null || cooldownSeconds < 0) {
            throw new IllegalStateException("Cooldown seconds must be non-negative");
        }
    }
}
