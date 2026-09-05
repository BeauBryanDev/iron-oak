package com.ironoak.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads a Bearer token and populates the SecurityContext when it verifies.
 *
 * A missing or bad token is not rejected here - the filter simply leaves the context
 * empty and lets the authorization rules decide. That is what keeps the public
 * storefront endpoints reachable without credentials.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AdminUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, AdminUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            jwtService.extractUsername(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(username -> authenticate(username, request));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String username, HttpServletRequest request) {
        try {
            UserDetails details = userDetailsService.loadUserByUsername(username);
            var authentication = new UsernamePasswordAuthenticationToken(
                    details, null, details.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException e) {
            // Token was valid but the account is gone - stay anonymous.
            SecurityContextHolder.clearContext();
        }
    }
}
