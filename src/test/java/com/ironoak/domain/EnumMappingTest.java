package com.ironoak.domain;

import com.ironoak.domain.enums.MessageRole;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.domain.enums.PricingType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the NAMED_ENUM mappings bind in both directions against the real
 * PostgreSQL enum types, not just that ddl-auto: validate accepts the schema.
 */
@SpringBootTest
@Testcontainers
@Transactional
class EnumMappingTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private EntityManager em;

    @Test
    void writesAndReadsEveryNativeEnumType() {
        Long orderId = ((Number) em.createNativeQuery("""
                INSERT INTO customer_order (status, channel, total_amount)
                VALUES ('CONFIRMED', 'AGENT_CHAT', 149.99) RETURNING id
                """).getSingleResult()).longValue();

        Long categoryId = ((Number) em.createNativeQuery(
                "INSERT INTO service_category (name) VALUES ('Plumbing') RETURNING id")
                .getSingleResult()).longValue();

        Long serviceId = ((Number) em.createNativeQuery("""
                INSERT INTO service (service_category_id, name, pricing_type, hourly_rate,
                                     estimated_min_hours, estimated_max_hours, is_active)
                VALUES (?, 'Leak repair', 'HOURLY', 45.00, 1.0, 3.0, true) RETURNING id
                """).setParameter(1, categoryId).getSingleResult()).longValue();

        em.createNativeQuery("""
                INSERT INTO order_item (order_id, item_type, service_id, quantity, unit_price, subtotal)
                VALUES (?, 'SERVICE', ?, 2, 45.00, 90.00)
                """).setParameter(1, orderId).setParameter(2, serviceId).executeUpdate();

        Long sessionId = ((Number) em.createNativeQuery(
                "INSERT INTO chat_session DEFAULT VALUES RETURNING id").getSingleResult()).longValue();

        em.createNativeQuery("""
                INSERT INTO chat_message (chat_session_id, role, content, tool_name)
                VALUES (?, 'TOOL', 'stock lookup returned 4 units', 'StockLookupTool')
                """).setParameter(1, sessionId).executeUpdate();

        em.flush();
        em.clear();

        // read path
        CustomerOrder order = em.find(CustomerOrder.class, orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getChannel()).isEqualTo(OrderChannel.AGENT_CHAT);

        Service service = em.find(Service.class, serviceId);
        assertThat(service.getPricingType()).isEqualTo(PricingType.HOURLY);

        OrderItem item = em.createQuery(
                "select i from OrderItem i where i.order.id = :id", OrderItem.class)
                .setParameter("id", orderId).getSingleResult();
        assertThat(item.getItemType()).isEqualTo(OrderItemType.SERVICE);

        ChatMessage message = em.createQuery(
                "select m from ChatMessage m where m.chatSession.id = :id", ChatMessage.class)
                .setParameter("id", sessionId).getSingleResult();
        assertThat(message.getRole()).isEqualTo(MessageRole.TOOL);

        // write path - the part validate cannot prove: binding a Java enum as a PG enum
        em.createQuery("update CustomerOrder o set o.status = :s where o.id = :id")
                .setParameter("s", OrderStatus.COMPLETED)
                .setParameter("id", orderId)
                .executeUpdate();
        em.clear();

        assertThat(em.find(CustomerOrder.class, orderId).getStatus())
                .isEqualTo(OrderStatus.COMPLETED);
    }
}
