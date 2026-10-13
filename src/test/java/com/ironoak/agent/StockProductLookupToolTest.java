package com.ironoak.agent;

import com.ironoak.agent.tools.StockProductLookupTool;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.services.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockProductLookupToolTest {

    @Test
    void returnsOnlyProductIdentityAndAuthoritativeStockQuantity() {
        ProductService products = mock(ProductService.class);
        StockProductLookupTool tool = new StockProductLookupTool(products);
        ProductResponse product = new ProductResponse(1L, "IO-HAM-001", "Hammer", "IronClad",
                "Hand Tools", "hammer_ai", "A hammer", new BigDecimal("12.50"), 7, true, 12,
                null, "Hand Tools", 2L, null);
        when(products.search("hammer", null, null, 5)).thenReturn(List.of(product));

        var result = tool.lookupProductStock("hammer", null, null);

        assertThat(result).singleElement().satisfies(stock -> {
            assertThat(stock.sku()).isEqualTo("IO-HAM-001");
            assertThat(stock.name()).isEqualTo("Hammer");
            assertThat(stock.stockQuantity()).isEqualTo(7);
        });
        verify(products).search("hammer", null, null, 5);
    }
}
