package com.example.ratelimiter.domain.repository;

import com.example.ratelimiter.domain.entity.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PolicyRuleRepository extends JpaRepository<PolicyRule, UUID> {

    /**
     * Find all policy rules for a given policy.
     *
     * @param policyId The policy ID
     * @return List of policy rules
     */
    List<PolicyRule> findByPolicyId(UUID policyId);

    /**
     * Find all enabled policy rules ordered by priority (descending).
     * Higher priority rules are returned first.
     *
     * @return List of enabled policy rules ordered by priority
     */
    @Query("SELECT pr FROM PolicyRule pr WHERE pr.enabled = true ORDER BY pr.priority DESC, pr.createdAt ASC")
    List<PolicyRule> findAllEnabledOrderedByPriority();

    /**
     * Find all enabled policy rules for a given policy ordered by priority.
     *
     * @param policyId The policy ID
     * @return List of enabled policy rules
     */
    @Query("SELECT pr FROM PolicyRule pr WHERE pr.policy.id = :policyId AND pr.enabled = true ORDER BY pr.priority DESC")
    List<PolicyRule> findEnabledByPolicyIdOrderedByPriority(@Param("policyId") UUID policyId);

    /**
     * Check if a policy rule with the given name already exists for the policy.
     *
     * @param name The rule name
     * @param policyId The policy ID
     * @return true if exists, false otherwise
     */
    boolean existsByNameAndPolicyId(String name, UUID policyId);

    /**
     * Find all enabled policy rules.
     *
     * @return List of enabled policy rules
     */
    List<PolicyRule> findByEnabledTrue();
}
