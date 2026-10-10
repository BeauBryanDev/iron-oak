package com.ironoak.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.ironoak.security.AdminUserDetailsService;
import com.ironoak.security.JwtAuthenticationFilter;
import com.ironoak.security.RateLimitFilter;

/**
 * Two zones, one filter chain:
 *
 * public - the storefront. Browsing the catalog, talking to Piper, and placing
 * a guest order require no account, because customers never have one.
 * admin - the dashboard. JWT bearer token, ROLE_ADMIN, backed by admin_user.
 * 
 * Stateless: no session is created, so the token is the only thing carrying
 * identity.
 * CSRF is disabled for the same reason - there is no cookie for an attacker to
 * ride.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http,
                        JwtAuthenticationFilter jwtFilter,
                        RateLimitFilter rateLimitFilter,
                        CorsConfigurationSource corsConfigurationSource) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // 401 for missing/!bad credentials rather than a redirect to a login page
                                // that does not exist in a JSON API.
                                .exceptionHandling(handling -> handling
                                                .authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))

                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                                // Spring Boot forwards unhandled errors to /error on a separate
                                                // ERROR dispatch. Without this, a 404 or a thrown exception on a
                                                // public route is re-evaluated by anyRequest() .
                                                .requestMatchers("/error").permitAll()

                                                // Health checks for Apache / uptime monitors; details stay staff-only.
                                                .requestMatchers(HttpMethod.GET, "/actuator/health",
                                                                "/actuator/health/**")
                                                .permitAll()

                                                // API docs; springdoc only serves them where enabled (local profile).
                                                .requestMatchers(HttpMethod.GET, "/swagger-ui", "/swagger-ui.html",
                                                                "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                                                .permitAll()

                                                // Staff login - must be reachable to obtain a token at all.
                                                // The credential (password or refresh token) is in the body.
                                                .requestMatchers(HttpMethod.POST, "/api/admin/auth/login",
                                                                "/api/admin/auth/refresh", "/api/admin/auth/logout")
                                                .permitAll()

                                                // Storefront: read-only catalog browsing.
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/products/**",
                                                                "/api/services/**",
                                                                "/api/materials/**")
                                                .permitAll()

                                                // Piper: chat, image classification, and guest checkout.
                                                .requestMatchers("/api/chat/**", "/api/vision/**").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/orders", "/api/complaints",
                                                                "/api/bookings", "/api/bookings/*/reschedule",
                                                                "/api/bookings/*/cancel",
                                                                "/api/warranty-claims", "/api/support-tickets",
                                                                "/api/service-quotes",
                                                                "/api/shipping/quote",
                                                                "/api/orders/*/checkout-session",
                                                                // Stripe calls this; the signature, not a login,
                                                                // authenticates it.
                                                                "/api/payments/stripe/webhook")
                                                .permitAll()
                                                // Customers read back their own orders, bookings and claims by proving
                                                // the
                                                // email on the record (?email=); the services enforce that match.
                                                .requestMatchers(HttpMethod.GET, "/api/orders/*",
                                                                "/api/bookings", "/api/bookings/*",
                                                                "/api/warranty-claims", "/api/warranty-claims/*",
                                                                "/api/service-quotes/*")
                                                .permitAll()

                                                // Everything under /api/admin and the dashboard is staff-only,
                                                // as is any catalog mutation.
                                                .requestMatchers("/api/admin/**", "/api/dashboard/**").hasRole("ADMIN")

                                                .anyRequest().authenticated())

                                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                                // Throttle before any authentication work is done.
                                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authenticationManager(AdminUserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) {

                DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder);

                return provider::authenticate;
        }
}
