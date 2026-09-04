package com.ironoak.security;

import com.ironoak.repository.AdminUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads staff accounts from admin_user. Customers are never authenticated. */
@Service
public class AdminUserDetailService implements UserDetailsService {

    private final AdminUserRepository adminUsers;

    public AdminUserDetailService(AdminUserRepository adminUsers) {
        this.adminUsers = adminUsers;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return adminUsers.findByUsername(username)
                .map(admin -> User.withUsername(admin.getUsername())
                        .password(admin.getPasswordHash())
                        .roles("ADMIN")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No admin user '" + username + "'"));
    }
}
