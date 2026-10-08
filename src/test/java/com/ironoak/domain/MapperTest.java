package com.ironoak.domain;

import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.PricingType;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.mapper.OrderMapper;
import com.ironoak.mapper.ProductMapper;
import com.ironoak.mapper.ServiceMapper;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.ProductRepository;
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

/** Maps seeded rows to response DTOs inside a transaction, as the services will. */
@SpringBootTest
@Testcontainers
@Transactional
class MapperTest {

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
    private CustomerOrderRepository orders;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ServiceMapper serviceMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private EntityManager em;

    @Test
    void mapsProduct() {
        var dto = productMapper.toResponse(products.findBySku("IO-AICO-001").orElseThrow());

        assertThat(dto.name()).isEqualTo("AIR COMPRESSORS");
        assertThat(dto.visionName()).isEqualTo("air_compressors");
        assertThat(dto.category()).isEqualTo("Power Tools");
        assertThat(dto.price()).isEqualByComparingTo("289.99");
        assertThat(dto.inStock()).isTrue();
    }

    @Test
    void mapsServicesAndMachines() {
        var services = em.createQuery("select s from ServiceOffering s order by s.code", ServiceOffering.class)
                .getResultList();
        var dtos = serviceMapper.toResponses(services);
        assertThat(dtos).hasSize(6);
        var emergency = dtos.stream().filter(d -> d.code().equals("EMERGENCY_REPAIR")).findFirst().orElseThrow();
        assertThat(emergency.pricingType()).isEqualTo(PricingType.HOURLY);
        assertThat(emergency.category()).isEqualTo("Maintenance & Repair");
        assertThat(emergency.hourlyRate()).isEqualByComparingTo("180.00");

        var machines = serviceMapper.toMachineResponses(
                em.createQuery("select m from MillingMachine m order by m.modelCode", MillingMachine.class)
                        .getResultList());
        assertThat(machines).extracting("modelCode").containsExactly("BM-200", "HB-900", "TM-450", "VMC-650");
    }

    @Test
    void mapsMixedOrder() {
        Long customerId = ((Number) em.createNativeQuery(
                "INSERT INTO customer (name) VALUES ('Jane Doe') RETURNING id").getSingleResult()).longValue();
        Long orderId = ((Number) em.createNativeQuery("""
                INSERT INTO customer_order (customer_id, status, channel, total_amount)
                VALUES (?, 'CONFIRMED', 'AGENT_CHAT', 0) RETURNING id
                """).setParameter(1, customerId).getSingleResult()).longValue();
        em.createNativeQuery("""
                INSERT INTO order_item (order_id, item_type, product_id, quantity, unit_price, subtotal)
                SELECT ?, 'PRODUCT', id, 2, 10.00, 20.00 FROM product WHERE sku = 'IO-AICO-001'
                """).setParameter(1, orderId).executeUpdate();
        em.createNativeQuery("""
                INSERT INTO order_item (order_id, item_type, service_offering_id, quantity, unit_price, subtotal)
                SELECT ?, 'SERVICE', id, 1, 240.00, 240.00 FROM service_offering WHERE code = 'PREVENTIVE_MAINTENANCE'
                """).setParameter(1, orderId).executeUpdate();
        em.createNativeQuery("""
                INSERT INTO order_item (order_id, item_type, milling_machine_id, quantity, unit_price, subtotal)
                SELECT ?, 'MACHINE', id, 1, 4250.00, 4250.00 FROM milling_machine WHERE model_code = 'BM-200'
                """).setParameter(1, orderId).executeUpdate();
        em.flush();
        em.clear();

        OrderResponse dto = orderMapper.toResponse(orders.findWithItemsById(orderId).orElseThrow());

        assertThat(dto.customerName()).isEqualTo("Jane Doe");
        assertThat(dto.items()).hasSize(3);
        assertThat(dto.items()).extracting("itemType")
                .containsExactlyInAnyOrder(OrderItemType.PRODUCT, OrderItemType.SERVICE, OrderItemType.MACHINE);
        assertThat(dto.items()).extracting("name")
                .containsExactlyInAnyOrder("AIR COMPRESSORS", "Preventive Maintenance", "Benchtop Mill");
    }
}
