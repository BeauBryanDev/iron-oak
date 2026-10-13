package com.ironoak.agent;

import com.ironoak.agent.tools.OrderTools;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.services.OrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderToolsTest {

    @Test
    void createDelegatesToPiperChannelAndDoesNotHandlePayment() {
        OrderService orders = mock(OrderService.class);
        OrderTools tools = new OrderTools(orders);
        OrderResponse response = mock(OrderResponse.class);
        when(orders.create(any(CreateOrderRequest.class), eq(OrderChannel.PIPER), eq("retry-1")))
                .thenReturn(response);

        var result = tools.createOrder(
                new OrderTools.Customer("Jane", "jane@example.com", null,
                        "CO", null, "Bogota", "Calle 1"),
                List.of(new OrderTools.Item(OrderItemType.PRODUCT, 42L, 2, null)),
                "retry-1");

        assertThat(result).isSameAs(response);
        verify(orders).create(any(CreateOrderRequest.class), eq(OrderChannel.PIPER), eq("retry-1"));
    }

    @Test
    void getUsesOrderNumberAndEmail() {
        OrderService orders = mock(OrderService.class);
        OrderTools tools = new OrderTools(orders);
        OrderResponse response = mock(OrderResponse.class);
        when(orders.getForCustomer("IO-20261012-ABCD2345", "jane@example.com"))
                .thenReturn(response);

        assertThat(tools.getOrder("IO-20261012-ABCD2345", "jane@example.com"))
                .isSameAs(response);
        verify(orders).getForCustomer("IO-20261012-ABCD2345", "jane@example.com");
    }
}
