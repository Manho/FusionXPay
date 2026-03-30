package com.fusionxpay.admin.controller;

import com.fusionxpay.admin.dto.*;
import com.fusionxpay.admin.model.MerchantStatus;
import com.fusionxpay.admin.service.MerchantManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/merchants")
@RequiredArgsConstructor
@Tag(name = "Merchant Management", description = "Merchant management endpoints for admins")
@PreAuthorize("hasRole('ADMIN')")
public class MerchantManagementController {

    private final MerchantManagementService merchantManagementService;

    @GetMapping
    @Operation(summary = "List merchants", description = "Get paged merchant list")
    public ResponseEntity<MerchantPageResponse> listMerchants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) MerchantStatus status) {
        return ResponseEntity.ok(merchantManagementService.listMerchants(page, size, keyword, status));
    }

    @GetMapping("/{merchantId}")
    @Operation(summary = "Get merchant detail")
    public ResponseEntity<MerchantInfo> getMerchant(@PathVariable Long merchantId) {
        return ResponseEntity.ok(merchantManagementService.getMerchant(merchantId));
    }

    @PostMapping
    @Operation(summary = "Create merchant")
    public ResponseEntity<MerchantInfo> createMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        MerchantInfo merchantInfo = merchantManagementService.createMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantInfo);
    }

    @PatchMapping("/{merchantId}/status")
    @Operation(summary = "Update merchant status")
    public ResponseEntity<MerchantInfo> updateMerchantStatus(@PathVariable Long merchantId,
                                                             @Valid @RequestBody UpdateMerchantStatusRequest request) {
        return ResponseEntity.ok(merchantManagementService.updateMerchantStatus(merchantId, request.getStatus()));
    }
}
