package com.example.ratelimiter.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(nullable = false)
    private String name;

    @Column(name = "resource_pattern", nullable = false, length = 500)
    private String resourcePattern;

    @Column(name = "http_methods", length = 100)
    private String httpMethods;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Checks if this rule matches the given HTTP method.
     * If httpMethods is null or empty, the rule matches all methods.
     *
     * @param method The HTTP method to check (e.g., "GET", "POST")
     * @return true if the method matches, false otherwise
     */
    public boolean matchesHttpMethod(String method) {
        if (httpMethods == null || httpMethods.trim().isEmpty()) {
            return true; // null or empty means all methods
        }

        if (method == null) {
            return false;
        }

        String[] methods = httpMethods.split(",");
        for (String m : methods) {
            if (m.trim().equalsIgnoreCase(method.trim())) {
                return true;
            }
        }
        return false;
    }
}
