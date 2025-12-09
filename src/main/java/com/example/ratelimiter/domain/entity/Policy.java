package com.example.ratelimiter.domain.entity;

import com.example.ratelimiter.domain.enums.Algorithm;
import com.example.ratelimiter.domain.enums.FailMode;
import com.example.ratelimiter.domain.enums.PolicyScope;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Algorithm algorithm;

    @Column(name = "max_requests", nullable = false)
    private Integer maxRequests;

    @Column(name = "window_seconds", nullable = false)
    private Integer windowSeconds;

    @Column(name = "burst_capacity")
    private Integer burstCapacity;

    @Column(name = "refill_rate", precision = 10, scale = 4)
    private BigDecimal refillRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_mode", nullable = false)
    @Builder.Default
    private FailMode failMode = FailMode.FAIL_CLOSED;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
