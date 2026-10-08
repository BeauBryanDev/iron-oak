package com.ironoak.domain;

import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.CustomerRepository;
import com.ironoak.repository.OrderItemRepository;
import com.ironoak.repository.ProductRepository;
import com.ironoak.repository.ToolCategoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the repository queries against the Flyway-migrated and seeded schema. */
@SpringBootTest
@Testcontainers
@Transactional
class RepositoryQueriesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ProductRepository products;

    @Autowired
    private ToolCategoryRepository toolCategories;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CustomerOrderRepository orders;

    @Autowired
    private OrderItemRepository orderItems;

    @Autowired
    private EntityManager em;

    @Test
    void catalogQueriesReturnSeededData() {
        assertThat(products.count()).isEqualTo(87);
        assertThat(toolCategories.findAllByOrderByDisplayNameAsc()).hasSize(87);

        var wrench = products.findByVisionNameAndIsActiveTrue("wrench_ai");
        assertThat(wrench).hasSize(1);
        assertThat(wrench.get(0).getToolCategory().getModelLabel()).isEqualTo("wrench_ai");

        assertThat(products.findActiveCategories())
                .contains("Hand Tools", "Safety Equipment", "Electrical")
                .doesNotContain("metal_nut", "power_socket", "Bearing");

        assertThat(products.findByCategoryAndIsActiveTrue("Hand Tools", PageRequest.of(0, 50))
                .getTotalElements()).isEqualTo(27);
        assertThat(products.findByNameContainingIgnoreCaseAndIsActiveTrue("hammer", PageRequest.of(0, 10))
                .getTotalElements()).isGreaterThan(0);
    }

    @Test
    void decrementStockOnlyWhenEnoughIsAvailable() {
        Product product = products.findByIsActiveTrue(PageRequest.of(0, 1)).getContent().get(0);
        int stock = product.getStockQuantity();

        assertThat(products.decrementStock(product.getId(), stock + 1)).isZero();
        assertThat(products.decrementStock(product.getId(), 1)).isEqualTo(1);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(stock - 1);
    }

    @Test
    void customerAndOrderQueries() {
        em.createNativeQuery("INSERT INTO customer (name, email) VALUES ('Jane Doe', 'Jane@Example.com')")
                .executeUpdate();
        em.createNativeQuery("""
                INSERT INTO customer_order (status, channel, total_amount)
                VALUES ('COMPLETED', 'AGENT_CHAT', 100.50), ('COMPLETED', 'ADMIN_MANUAL', 20.00),
                       ('DRAFT', 'AGENT_CHAT', 5.00)
                """).executeUpdate();
        em.flush();
        em.clear();

        assertThat(customers.findFirstByEmailIgnoreCase("jane@example.com")).isPresent();
        assertThat(orders.countByStatus(OrderStatus.COMPLETED)).isEqualTo(2);
        assertThat(orders.sumTotalByStatus(OrderStatus.COMPLETED))
                .isEqualByComparingTo(new BigDecimal("120.50"));
        assertThat(orders.findByStatus(OrderStatus.DRAFT, PageRequest.of(0, 10)).getContent()).hasSize(1);
    }

    @Test
    void orderItemQueriesRankTopSellingProducts() {
        var picked = products.findByIsActiveTrue(PageRequest.of(0, 2)).getContent();
        Long first = picked.get(0).getId();
        Long second = picked.get(1).getId();

        Long orderId = ((Number) em.createNativeQuery("""
                INSERT INTO customer_order (status, channel, total_amount)
                VALUES ('COMPLETED', 'AGENT_CHAT', 10.00) RETURNING id
                """).getSingleResult()).longValue();
        em.createNativeQuery("""
                INSERT INTO order_item (order_id, item_type, product_id, quantity, unit_price, subtotal)
                VALUES (?1, 'PRODUCT', ?2, 1, 5.00, 5.00), (?1, 'PRODUCT', ?3, 4, 5.00, 20.00)
                """).setParameter(1, orderId).setParameter(2, first).setParameter(3, second).executeUpdate();
        em.flush();
        em.clear();

        assertThat(orderItems.findByOrderId(orderId)).hasSize(2);

        var top = orderItems.findTopSellingProducts(OrderStatus.COMPLETED, PageRequest.of(0, 5));
        assertThat(top).hasSize(2);
        assertThat(top.get(0).getProductId()).isEqualTo(second);
        assertThat(top.get(0).getUnitsSold()).isEqualTo(4L);
    }
}
