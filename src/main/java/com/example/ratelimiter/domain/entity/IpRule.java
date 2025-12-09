package com.example.ratelimiter.domain.entity;

import com.example.ratelimiter.domain.enums.RuleType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ip_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IpRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "ip_cidr")
    private String ipCidr;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private RuleType ruleType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private Policy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validateIpRule() {
        if (ipAddress == null && ipCidr == null) {
            throw new IllegalStateException("Either ipAddress or ipCidr must be provided");
        }
        if (ipAddress != null && ipCidr != null) {
            throw new IllegalStateException("Only one of ipAddress or ipCidr can be provided");
        }
        if (!RuleType.RATE_LIMIT.equals(ruleType)) {
            throw new IllegalStateException("Only RATE_LIMIT rule type is currently supported");
        }
        if (policy == null) {
            throw new IllegalStateException("Policy is required for RATE_LIMIT rule type");
        }
    }
}
