package com.ironoak.security;

import java.util.List;
import java.time.temporal.ChronoUnit;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.ironoak.repository.AdminUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads a Bearer token and populates the SecurityContext when it verifies.
 *
 * A missing or bad token is not rejected here - the filter simply leaves the
 * context
 * empty and lets the authorization rules decide. That is what keeps the public
 * storefront endpoints reachable without credentials.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AdminUserRepository adminUsers;

    public JwtAuthenticationFilter(JwtService jwtService, AdminUserRepository adminUsers) {
        this.jwtService = jwtService;
        this.adminUsers = adminUsers;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            jwtService.parse(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(token -> authenticate(token, request));
        }

        filterChain.doFilter(request, response);
    }

    /** Granted while a temporary password is in use: reaches only change-password, /me and logout-all. */
    public static final String ROLE_PASSWORD_CHANGE_REQUIRED = "ROLE_PASSWORD_CHANGE_REQUIRED";

    /**
     * The account must still exist and be active, and the token must not predate its last
     * password change, so changing the password (or disabling the account) kills every access
     * token issued before it.
     */
    private void authenticate(JwtService.AccessToken token, HttpServletRequest request) {
        adminUsers.findByUsername(token.username())
                .filter(admin -> admin.isActive())
                .filter(admin -> !token.issuedAt().isBefore(
                        admin.getPasswordChangedAt().toInstant().truncatedTo(ChronoUnit.SECONDS)))
                .ifPresent(admin -> {
                    String role = admin.isMustChangePassword() ? ROLE_PASSWORD_CHANGE_REQUIRED : "ROLE_ADMIN";
                    var authentication = new UsernamePasswordAuthenticationToken(
                            admin.getUsername(), null, List.of(new SimpleGrantedAuthority(role)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
    }
}
