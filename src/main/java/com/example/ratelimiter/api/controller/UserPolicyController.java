package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.UserPolicyRequest;
import com.example.ratelimiter.application.dto.UserPolicyResponse;
import com.example.ratelimiter.application.service.UserPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user-policies")
@RequiredArgsConstructor
public class UserPolicyController {

    private final UserPolicyService userPolicyService;

    @GetMapping
    public ResponseEntity<List<UserPolicyResponse>> findAll() {
        return ResponseEntity.ok(userPolicyService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserPolicyResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(userPolicyService.findById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<UserPolicyResponse> findByUserId(
            @PathVariable String userId,
            @RequestParam UUID tenantId) {
        return ResponseEntity.ok(userPolicyService.findByUserId(userId, tenantId));
    }

    @PostMapping
    public ResponseEntity<UserPolicyResponse> create(@Valid @RequestBody UserPolicyRequest request) {
        UserPolicyResponse response = userPolicyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserPolicyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UserPolicyRequest request) {
        return ResponseEntity.ok(userPolicyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userPolicyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
