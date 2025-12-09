package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.ApiKeyCreatedResponse;
import com.example.ratelimiter.application.dto.ApiKeyRequest;
import com.example.ratelimiter.application.dto.ApiKeyResponse;
import com.example.ratelimiter.application.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> findAll() {
        return ResponseEntity.ok(apiKeyService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(apiKeyService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> create(@Valid @RequestBody ApiKeyRequest request) {
        ApiKeyCreatedResponse response = apiKeyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ApiKeyRequest request) {
        return ResponseEntity.ok(apiKeyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        apiKeyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
