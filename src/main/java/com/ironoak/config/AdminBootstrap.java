package com.ironoak.config;

import com.ironoak.domain.AdminUser;
import com.ironoak.domain.enums.AuditAction;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.security.PasswordPolicy;
import com.ironoak.services.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Creates the first production admin, once. Reads BOOTSTRAP_ADMIN_USERNAME, _EMAIL and
 * _PASSWORD (server .env, never Git) and acts only while admin_user is empty: with any admin
 * present it does nothing, so restarts can never add or overwrite an account. The password is
 * temporary (must be changed at first login) and a weak one stops the app at startup.
 * After the first login, change the password and delete the three lines from .env.
 * The local profile uses DevAdminSeeder instead.
 */
@Component
@Profile("!local")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserRepository adminUsers;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Environment environment;

    public AdminBootstrap(AdminUserRepository adminUsers,
            PasswordEncoder passwordEncoder,
            AuditService audit,
            Environment environment) {
        this.adminUsers = adminUsers;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {

        String username = trimmed("BOOTSTRAP_ADMIN_USERNAME");
        String email = trimmed("BOOTSTRAP_ADMIN_EMAIL");
        String password = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD");

        if (adminUsers.count() > 0) {
            if (username != null || password != null) {
                log.warn("Admin accounts already exist: BOOTSTRAP_ADMIN_* ignored. Remove them from .env.");
            }
            return;
        }
        if (username == null || email == null || password == null || password.isBlank()) {
            log.warn("No admin account exists and BOOTSTRAP_ADMIN_USERNAME / _EMAIL / _PASSWORD are not all set:"
                    + " nobody can sign in to /api/admin until they are.");
            return;
        }
        PasswordPolicy.validate(password, username, null); // a weak bootstrap password stops startup

        AdminUser admin = new AdminUser(username, email.toLowerCase(Locale.ROOT), "", "Owner");
        admin.setTemporaryPassword(passwordEncoder.encode(password));
        adminUsers.saveAndFlush(admin);
        audit.recordAuth(AuditAction.ADMIN_BOOTSTRAP, username, null, null);
        log.warn("Bootstrap admin '{}' created. Sign in, change the password, then delete BOOTSTRAP_ADMIN_* from .env.",
                username);
    }

    private String trimmed(String key) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
