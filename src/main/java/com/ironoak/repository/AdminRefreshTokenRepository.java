package com.ironoak.repository;

import com.ironoak.domain.AdminRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AdminRefreshTokenRepository extends JpaRepository<AdminRefreshToken, Long> {

        Optional<AdminRefreshToken> findByTokenHash(String tokenHash);

        /**
         * Row-locking lookup for rotation: concurrent uses of one token are processed
         * one after the other.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT t FROM AdminRefreshToken t WHERE t.tokenHash = :tokenHash")
        Optional<AdminRefreshToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

        /**
         * Revokes one token only if it is still live, so two concurrent uses cannot
         * both win. Returns 1 or 0.
         */
        @Modifying(flushAutomatically = true)
        @Query("UPDATE AdminRefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason "
                        + "WHERE t.id = :id AND t.revokedAt IS NULL")
        int revoke(@Param("id") Long id,
                        @Param("now") OffsetDateTime now,
                        @Param("reason") String reason);

        @Modifying(flushAutomatically = true)
        @Query("UPDATE AdminRefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason "
                        + "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
        int revokeFamily(@Param("familyId") UUID familyId,
                        @Param("now") OffsetDateTime now,
                        @Param("reason") String reason);

        @Modifying(flushAutomatically = true)
        @Query("UPDATE AdminRefreshToken t SET t.revokedAt = :now, t.revokedReason = :reason "
                        + "WHERE t.adminUser.id = :adminUserId AND t.revokedAt IS NULL")
        int revokeAllForUser(@Param("adminUserId") Long adminUserId,
                        @Param("now") OffsetDateTime now,
                        @Param("reason") String reason);

        @Modifying
        @Query("DELETE FROM AdminRefreshToken t WHERE t.expiresAt < :cutoff")
        int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
