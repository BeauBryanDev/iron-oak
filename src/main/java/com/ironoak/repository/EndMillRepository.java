package com.ironoak.repository;

import com.ironoak.domain.EndMill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EndMillRepository extends JpaRepository<EndMill, Long>,
        JpaSpecificationExecutor<EndMill> {

    /**
     * sku is not unique in the database (no index by design); services check it
     * here.
     */
    Optional<EndMill> findFirstBySkuIgnoreCase(String sku);

    Optional<EndMill> findByVisionLabel(String visionLabel);

    /**
     * Flute types on sale, for the storefront filter (e.g. Ball Nose, Fishtail).
     */
    @Query("SELECT DISTINCT e.fluteType FROM EndMill e WHERE e.isActive = true ORDER BY e.fluteType")
    List<String> findActiveFluteTypes();

    /**
     * Takes stock for an order atomically, like ProductRepository.decrementStock.
     * Returns 0 when
     * the end mill is short or inactive. Clears the persistence context: run after
     * all lookups.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EndMill e SET e.stockQuantity = e.stockQuantity - :quantity
            WHERE e.id = :id AND e.stockQuantity >= :quantity AND e.isActive = true
            """)
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** Returns stock taken by a cancelled or expired order. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EndMill e SET e.stockQuantity = e.stockQuantity + :quantity WHERE e.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * Staff stock correction, positive or negative; returns 0 when it would go
     * below zero.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EndMill e SET e.stockQuantity = e.stockQuantity + :delta WHERE e.id = :id AND e.stockQuantity + :delta >= 0")
    int adjustStock(@Param("id") Long id, @Param("delta") int delta);
}
