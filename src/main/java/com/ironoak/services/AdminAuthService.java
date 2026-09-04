package com.ironoak.services;

import com.ironoak.domain.AdminUser;
import com.ironoak.dto.request.AdminLoginRequest;
import com.ironoak.dto.response.AdminLoginResponse;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.security.JWTService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

    private final AuthenticationManager authenticationManager;
    private final AdminUserRepository adminUsers;
    private final JWTService jwtService;

    public AdminAuthService(AuthenticationManager authenticationManager,
                            AdminUserRepository adminUsers,
                            JWTService jwtService) {
        this.authenticationManager = authenticationManager;
        this.adminUsers = adminUsers;
        this.jwtService = jwtService;
    }

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        // Throws BadCredentialsException for both a wrong password and an unknown
        // username, so the response cannot be used to enumerate accounts.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        AdminUser admin = adminUsers.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        admin.recordLogin();

        return new AdminLoginResponse(
                jwtService.issueToken(admin.getUsername()),
                admin.getUsername(),
                admin.getFullName(),
                jwtService.getExpirationMinutes());
    }
}
