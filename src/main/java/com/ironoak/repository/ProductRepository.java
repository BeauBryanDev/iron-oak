package com.ironoak.repository;

import com.ironoak.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySku(String sku);

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Page<Product> findByCategoryAndIsActiveTrue(String category, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndIsActiveTrue(String name, Pageable pageable);

    /** Products for a vision prediction label (tool_category.model_label). */
    List<Product> findByVisionNameAndIsActiveTrue(String visionName);

    /** Distinct storefront categories, for the catalog sidebar. */
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.isActive = true ORDER BY p.category")
    List<String> findActiveCategories();

    /**
     * Atomically takes stock. Returns 1 when the units were available and are now
     * reserved, 0 when stock is insufficient - the caller maps 0 to
     * OutOfStockException.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Product p set p.stockQuantity = p.stockQuantity - :quantity
            WHERE p.id = :id AND p.stockQuantity >= :quantity
            AND p.isActive = true
            """)
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** Returns stock taken by a cancelled order. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity WHERE p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * Staff stock correction, positive or negative. Returns 0 when it would take
     * stock below zero. Unlike decrementStock it also works on inactive products.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :delta WHERE p.id = :id AND p.stockQuantity + :delta >= 0")
    int adjustStock(@Param("id") Long id, @Param("delta") int delta);

    /** Method to search by VisionName + category */
    List<Product> findByVisionNameAndCategoryAndIsActiveTrue(String visionName, String category);
}
