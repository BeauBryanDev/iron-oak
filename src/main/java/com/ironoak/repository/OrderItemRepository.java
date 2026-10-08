package com.ironoak.repository;

import com.ironoak.domain.OrderItem;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    /** Best-selling products by units across orders in the given status, for the dashboard. */
    @Query("""
            select i.product.id as productId, i.product.name as name, sum(i.quantity) as unitsSold
            from OrderItem i
            where i.itemType = :itemType
              and i.order.status = :status
            group by i.product.id, i.product.name
            order by sum(i.quantity) desc
            """)
    List<TopProduct> findTopSellingProducts(@Param("itemType") OrderItemType itemType,
                                            @Param("status") OrderStatus status, Pageable pageable);

    default List<TopProduct> findTopSellingProducts(OrderStatus status, Pageable pageable) {
        return findTopSellingProducts(OrderItemType.PRODUCT, status, pageable);
    }

    interface TopProduct {
        Long getProductId();

        String getName();

        Long getUnitsSold();
    }
}
