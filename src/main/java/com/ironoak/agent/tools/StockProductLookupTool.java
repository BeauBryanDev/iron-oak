package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.ProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Fast, read-only product stock lookup for Piper. */
@Component
public class StockProductLookupTool {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;

    private final ProductService products;

    public StockProductLookupTool(ProductService products) {
        this.products = products;
    }

    @Tool(name = "lookup_product_stock", description = "Quickly return current stock quantities for active products matching a product name, SKU, or exact vision name. Returns only product identity and stock quantity; stock comes from the database and must never be inferred.")
    public List<StockResult> lookupProductStock(
            @ToolParam(required = false, description = "Product name or SKU search text.") String query,
            @ToolParam(required = false, description = "Exact CNN vision label for the product.") String vision_name,
            @ToolParam(required = false, description = "Number of matching products, default 5, maximum 10.") Integer limit) {

        if ((query == null || query.isBlank()) && (vision_name == null || vision_name.isBlank())) {
            throw new BusinessRuleException("query or vision_name is required");
        }

        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new BusinessRuleException("limit must be between 1 and 10");
        }

        return products.search(query, null, vision_name, effectiveLimit).stream()
                .map(StockResult::from)
                .toList();
    }

    public record StockResult(
            String sku,
            String name,
            @JsonProperty("stock_quantity") int stockQuantity) {

        static StockResult from(ProductResponse product) {
            return new StockResult(product.sku(), product.name(), product.stockQuantity());
        }
    }
}
