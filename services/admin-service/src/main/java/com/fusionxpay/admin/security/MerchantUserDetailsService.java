package com.fusionxpay.admin.security;

import com.fusionxpay.admin.model.Merchant;
import com.fusionxpay.admin.model.MerchantStatus;
import com.fusionxpay.admin.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Custom UserDetailsService for merchant authentication
 */
@Service
@RequiredArgsConstructor
public class MerchantUserDetailsService implements UserDetailsService {

    private final MerchantRepository merchantRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Merchant merchant = merchantRepository.findByEmailAndStatus(email, MerchantStatus.ACTIVE)
                .orElseThrow(() -> new UsernameNotFoundException("Merchant not found with email: " + email));

        return new User(
                merchant.getEmail(),
                merchant.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + merchant.getRole().name()))
        );
    }
}
