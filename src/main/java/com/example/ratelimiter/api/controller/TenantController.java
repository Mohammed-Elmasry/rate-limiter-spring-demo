package com.example.ratelimiter.api.controller;

import com.example.ratelimiter.application.dto.TenantRequest;
import com.example.ratelimiter.application.dto.TenantResponse;
import com.example.ratelimiter.application.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public ResponseEntity<List<TenantResponse>> findAll() {
        return ResponseEntity.ok(tenantService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody TenantRequest request) {
        TenantResponse response = tenantService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody TenantRequest request) {
        return ResponseEntity.ok(tenantService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tenantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
