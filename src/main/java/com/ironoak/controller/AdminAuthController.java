package com.ironoak.controller;

import com.ironoak.dto.request.AdminLoginRequest;
import com.ironoak.dto.request.ChangePasswordRequest;
import com.ironoak.dto.request.RefreshTokenRequest;
import com.ironoak.dto.response.AdminLoginResponse;
import com.ironoak.dto.response.AdminProfileResponse;
import com.ironoak.security.ClientIpResolver;
import com.ironoak.services.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * login, refresh and logout are public: the credential is in the body.
 * Everything else here
 * is under /api/admin and needs a valid access token.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService auth;
    private final ClientIpResolver clientIp;

    public AdminAuthController(AdminAuthService auth,
            ClientIpResolver clientIp) {
        this.auth = auth;
        this.clientIp = clientIp;
    }

    @PostMapping("/login")
    public AdminLoginResponse login(@Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest http) {

        return auth.login(request, clientIp.resolve(http),
                http.getHeader("User-Agent"));
    }

    /**
     * Single-use: the response carries the next refresh token, and the one sent
     * here stops working.
     */
    @PostMapping("/refresh")
    public AdminLoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest http) {

        return auth.refresh(request.refreshToken(),
                clientIp.resolve(http),
                http.getHeader("User-Agent"));
    }

    /**
     * Ends the session of the given refresh token. Always 204, so it reveals
     * nothing about the token.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest http) {

        auth.logout(request.refreshToken(),

                clientIp.resolve(http));
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(Principal principal, HttpServletRequest http) {

        auth.logoutAll(principal.getName(),
                clientIp.resolve(http));
    }

    @GetMapping("/me")
    public AdminProfileResponse me(Principal principal) {

        return auth.me(principal.getName());
    }

    @PostMapping("/change-password")
    public AdminLoginResponse changePassword(Principal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest http) {

        return auth.changePassword(principal.getName(),
                request, clientIp.resolve(http),
                http.getHeader("User-Agent"));
    }
}
