package com.ironoak.repository;

import com.ironoak.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Page<Product> findByCategoryAndIsActiveTrue(String category, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndIsActiveTrue(String name, Pageable pageable);

    /** Products for a vision prediction label (tool_category.model_label). */
    List<Product> findByVisionNameAndIsActiveTrue(String visionName);

    /** Distinct storefront categories, for the catalog sidebar. */
    @Query("select distinct p.category from Product p where p.isActive = true order by p.category")
    List<String> findActiveCategories();

    /**
     * Atomically takes stock. Returns 1 when the units were available and are now
     * reserved, 0 when stock is insufficient - the caller maps 0 to OutOfStockException.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Product p set p.stockQuantity = p.stockQuantity - :quantity
            where p.id = :id and p.stockQuantity >= :quantity
            """)
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** Returns stock taken by a cancelled order. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :quantity where p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}
