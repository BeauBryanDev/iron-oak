package com.ironoak.repository;

import com.ironoak.domain.AdminUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    Optional<AdminUser> findByEmailIgnoreCase(String email);

    List<AdminUser> findAllByOrderByUsernameAsc();

    /**
     * Locks every active account, so two admins disabling each other at the same moment cannot
     * leave the shop with none.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdminUser a where a.active = true")
    List<AdminUser> lockActive();
}
