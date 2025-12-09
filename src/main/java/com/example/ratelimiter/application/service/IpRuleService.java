package com.example.ratelimiter.application.service;

import com.example.ratelimiter.application.dto.IpRuleRequest;
import com.example.ratelimiter.application.dto.IpRuleResponse;
import com.example.ratelimiter.domain.entity.IpRule;
import com.example.ratelimiter.domain.entity.Policy;
import com.example.ratelimiter.domain.entity.Tenant;
import com.example.ratelimiter.domain.enums.RuleType;
import com.example.ratelimiter.domain.repository.IpRuleRepository;
import com.example.ratelimiter.domain.repository.PolicyRepository;
import com.example.ratelimiter.domain.repository.TenantRepository;
import com.example.ratelimiter.infrastructure.config.CacheConfig;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class IpRuleService {

    private final IpRuleRepository ipRuleRepository;
    private final PolicyRepository policyRepository;
    private final TenantRepository tenantRepository;

    public List<IpRuleResponse> findAll() {
        return ipRuleRepository.findAll().stream()
                .map(IpRuleResponse::from)
                .toList();
    }

    @Cacheable(value = CacheConfig.IP_RULES_CACHE, key = "#id", unless = "#result == null")
    public IpRuleResponse findById(UUID id) {
        return ipRuleRepository.findById(id)
                .map(IpRuleResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("IP rule not found with id: " + id));
    }

    public List<IpRuleResponse> findByTenantId(UUID tenantId) {
        return ipRuleRepository.findByTenantId(tenantId).stream()
                .map(IpRuleResponse::from)
                .toList();
    }

    public List<IpRuleResponse> findByRuleType(String ruleType) {
        RuleType ruleTypeEnum = RuleType.valueOf(ruleType.toUpperCase());
        return ipRuleRepository.findByRuleType(ruleTypeEnum).stream()
                .map(IpRuleResponse::from)
                .toList();
    }

    public Optional<IpRuleResponse> findMatchingRateLimitRuleForIp(String ip) {
        return ipRuleRepository.findMatchingRuleLimitRuleForIp(ip)
                .map(IpRuleResponse::from);
    }

    public Optional<IpRuleResponse> findMatchingRateLimitRuleForIpAndTenant(String ip, UUID tenantId) {
        return ipRuleRepository.findMatchingRateLimitRuleForIpAndTenant(ip, tenantId)
                .map(IpRuleResponse::from);
    }

    @Cacheable(value = CacheConfig.IP_POLICIES_CACHE,
               key = "#ip + ':' + (#tenantId != null ? #tenantId.toString() : 'null')",
               unless = "#result == null || !#result.isPresent()")
    public Optional<Policy> getPolicyForIp(String ip, UUID tenantId) {
        Optional<IpRule> ipRule;

        if (tenantId != null) {
            ipRule = ipRuleRepository.findMatchingRateLimitRuleForIpAndTenant(ip, tenantId);
        } else {
            ipRule = ipRuleRepository.findMatchingRuleLimitRuleForIp(ip);
        }

        return ipRule.map(IpRule::getPolicy);
    }

    @Transactional
    @Caching(
        put = {
            @CachePut(value = CacheConfig.IP_RULES_CACHE, key = "#result.id()")
        },
        evict = {
            @CacheEvict(value = CacheConfig.IP_POLICIES_CACHE, allEntries = true)
        }
    )
    public IpRuleResponse create(IpRuleRequest request) {
        Policy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));

        Tenant tenant = null;
        if (request.tenantId() != null) {
            tenant = tenantRepository.findById(request.tenantId())
                    .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + request.tenantId()));
        }

        IpRule ipRule = IpRule.builder()
                .ipAddress(request.ipAddress())
                .ipCidr(request.ipCidr())
                .ruleType(request.ruleType())
                .policy(policy)
                .tenant(tenant)
                .description(request.description())
                .enabled(request.enabled())
                .build();

        IpRule saved = ipRuleRepository.save(ipRule);
        log.info("Created IP rule: {} for IP: {}{}", saved.getId(),
                 saved.getIpAddress() != null ? saved.getIpAddress() : saved.getIpCidr(),
                 saved.getTenant() != null ? " (tenant: " + saved.getTenant().getId() + ")" : "");

        return IpRuleResponse.from(saved);
    }

    @Transactional
    @Caching(
        put = {
            @CachePut(value = CacheConfig.IP_RULES_CACHE, key = "#id")
        },
        evict = {
            @CacheEvict(value = CacheConfig.IP_POLICIES_CACHE, allEntries = true)
        }
    )
    public IpRuleResponse update(UUID id, IpRuleRequest request) {
        IpRule ipRule = ipRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("IP rule not found with id: " + id));

        Policy policy = policyRepository.findById(request.policyId())
                .orElseThrow(() -> new EntityNotFoundException("Policy not found with id: " + request.policyId()));

        Tenant tenant = null;
        if (request.tenantId() != null) {
            tenant = tenantRepository.findById(request.tenantId())
                    .orElseThrow(() -> new EntityNotFoundException("Tenant not found with id: " + request.tenantId()));
        }

        ipRule.setIpAddress(request.ipAddress());
        ipRule.setIpCidr(request.ipCidr());
        ipRule.setRuleType(request.ruleType());
        ipRule.setPolicy(policy);
        ipRule.setTenant(tenant);
        ipRule.setDescription(request.description());
        ipRule.setEnabled(request.enabled());

        IpRule saved = ipRuleRepository.save(ipRule);
        log.info("Updated IP rule: {}", saved.getId());

        return IpRuleResponse.from(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.IP_RULES_CACHE, key = "#id"),
        @CacheEvict(value = CacheConfig.IP_POLICIES_CACHE, allEntries = true)
    })
    public void delete(UUID id) {
        if (!ipRuleRepository.existsById(id)) {
            throw new EntityNotFoundException("IP rule not found with id: " + id);
        }
        ipRuleRepository.deleteById(id);
        log.info("Deleted IP rule: {}", id);
    }
}
