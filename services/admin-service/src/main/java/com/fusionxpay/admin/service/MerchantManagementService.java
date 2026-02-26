package com.fusionxpay.admin.service;

import com.fusionxpay.admin.dto.CreateMerchantRequest;
import com.fusionxpay.admin.dto.MerchantInfo;
import com.fusionxpay.admin.dto.MerchantListItem;
import com.fusionxpay.admin.dto.MerchantPageResponse;
import com.fusionxpay.admin.exception.ConflictException;
import com.fusionxpay.admin.exception.ResourceNotFoundException;
import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantRole;
import com.fusionxpay.admin.model.MerchantStatus;
import com.fusionxpay.admin.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantManagementService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public MerchantPageResponse listMerchants(int page, int size, String keyword, MerchantStatus status) {
        Pageable pageable = PageRequest.of(page, size);

        Specification<Merchant> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String trimmedKeyword = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("merchantName")), trimmedKeyword),
                    cb.like(cb.lower(root.get("email")), trimmedKeyword),
                    cb.like(cb.lower(root.get("merchantCode")), trimmedKeyword)
            ));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<Merchant> merchantPage = merchantRepository.findAll(spec, pageable);
        return MerchantPageResponse.builder()
                .merchants(merchantPage.getContent().stream().map(MerchantListItem::fromEntity).toList())
                .page(merchantPage.getNumber())
                .size(merchantPage.getSize())
                .totalElements(merchantPage.getTotalElements())
                .totalPages(merchantPage.getTotalPages())
                .first(merchantPage.isFirst())
                .last(merchantPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public MerchantInfo getMerchant(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));
        return MerchantInfo.fromEntity(merchant);
    }

    @Transactional
    public MerchantInfo createMerchant(CreateMerchantRequest request) {
        if (merchantRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        String merchantCode = request.getMerchantCode();
        if (merchantCode == null || merchantCode.isBlank()) {
            merchantCode = generateMerchantCode();
        }

        if (merchantRepository.existsByMerchantCode(merchantCode)) {
            throw new ConflictException("Merchant code already exists");
        }

        MerchantRole role = request.getRole() == null ? MerchantRole.MERCHANT : request.getRole();

        Merchant merchant = Merchant.builder()
                .merchantName(request.getMerchantName())
                .email(request.getEmail())
                .merchantCode(merchantCode)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .status(MerchantStatus.ACTIVE)
                .build();

        Merchant saved = merchantRepository.save(merchant);
        return MerchantInfo.fromEntity(saved);
    }

    @Transactional
    public MerchantInfo updateMerchantStatus(Long merchantId, MerchantStatus status) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));
        merchant.setStatus(status);
        Merchant saved = merchantRepository.save(merchant);
        return MerchantInfo.fromEntity(saved);
    }

    private String generateMerchantCode() {
        return "MCH" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
