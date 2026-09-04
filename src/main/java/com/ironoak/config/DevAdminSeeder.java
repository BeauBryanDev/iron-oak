package com.ironoak.config;

import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates a throwaway dashboard login so the admin endpoints can be exercised locally.
 */
@Configuration
@Profile("local")
public class DevAdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);

    @Bean
    public ApplicationRunner seedDevAdmin(AdminUserRepository adminUsers,
                                          PasswordEncoder passwordEncoder,
                                          Environment environment) {
        return args -> {
            String username = environment.getProperty("DEV_ADMIN_USERNAME", "owner");
            String password = environment.getProperty("DEV_ADMIN_PASSWORD", "ironoak-dev");

            if (adminUsers.findByUsername(username).isPresent()) {
                log.info("Dev admin '{}' already present - leaving it alone", username);
                return;
            }

            adminUsers.save(new AdminUser(username, username + "@ironoak.local",
                    passwordEncoder.encode(password), "Local Dev Owner"));

            log.warn("""
                    
                    ============================================================
                    Seeded DEV-ONLY admin account (profile: local)
                      username: {}
                      password: {}
                    Never enable the 'local' profile outside your machine.
                    ============================================================
                    """, username, password);
        };
    }
}
