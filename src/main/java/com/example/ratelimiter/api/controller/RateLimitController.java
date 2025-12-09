package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.RateLimitCheckRequest;
import com.example.ratelimiter.application.dto.RateLimitCheckResponse;
import com.example.ratelimiter.application.service.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rate-limit")
@RequiredArgsConstructor
@Slf4j
public class RateLimitController {

    private final RateLimitService rateLimitService;

    @PostMapping("/check")
    public ResponseEntity<RateLimitCheckResponse> checkRateLimit(
            @Valid @RequestBody RateLimitCheckRequest request) {

        log.debug("Rate limit check request: identifier={}, scope={}, ip={}, tenant={}",
                  request.identifier(), request.scope(), request.ipAddress(), request.tenantId());

        RateLimitCheckResponse response = rateLimitService.checkRateLimit(request);

        // Add standard rate limit headers
        return ResponseEntity.ok()
                .header("X-RateLimit-Limit", String.valueOf(response.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(response.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(response.resetInSeconds()))
                .body(response);
    }
}
