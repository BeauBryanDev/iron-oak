package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.ProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Lets Piper turn advice into products: after explaining how to fix something
 * (for example a dripping faucet, from home_do_it_yourself), it lists what Iron
 * Oak sells in the matching category (Plumbing).
 */
@Component
public class SearchProductsByCategoryTool {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ProductService products;

    public SearchProductsByCategoryTool(ProductService products) {
        this.products = products;
    }

    @Tool(name = "search_products_by_category", description = "List the active products of one storefront category with price, stock and reference_id (item_type PRODUCT). Use it after how-to or repair advice to suggest the few products the job needs: faucets, pipes, leaks -> Plumbing; wiring, sockets, testing -> Electrical; painting, sealing -> Paint & Finishes; drilling, cutting, grinding -> Power Tools; general repairs, measuring -> Hand Tools; screws, nails, chains -> Fasteners & Hardware; concrete, masonry -> Building Materials; protection -> Safety Equipment; yard work -> Outdoor & Garden; storage, ladders -> Workshop & Storage. Recommend only returned products.")
    public List<CategoryProduct> searchProductsByCategory(
            @ToolParam(description = "Storefront category, one of: Hand Tools, Power Tools, Electrical, "
                    + "Plumbing, Paint & Finishes, Safety Equipment, Outdoor & Garden, Workshop & Storage, "
                    + "Building Materials, Fasteners & Hardware.") String category,
            @ToolParam(required = false, description = "Number of products, default 20, maximum 50.")
            Integer limit) {

        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;

        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new BusinessRuleException("limit must be between 1 and " + MAX_LIMIT);
        }
        String resolved = resolveCategory(category);

        return products.search(null,
                        resolved,
                        null,
                        effectiveLimit)
                .stream()
                .map(CategoryProduct::from)
                .toList();
    }

    /**
     * Matches the model's wording to a real category, ignoring case and "and"
     * versus "&"; an unknown name fails with the list of valid ones, so the
     * model can correct itself.
     */
    private String resolveCategory(String category) {

        List<String> categories = products.categories();

        if (category == null || category.isBlank()) {
            throw new BusinessRuleException("category is required. Valid categories: "
                    + String.join(", ", categories));
        }
        String wanted = normalize(category);

        return categories.stream()
                .filter(c -> normalize(c).equals(wanted))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Unknown category '" + category.strip()
                        + "'. Valid categories: " + String.join(", ", categories)));
    }

    private static String normalize(String value) {

        return value.strip()
                .toLowerCase(Locale.ROOT)
                .replace(" and ", " & ")
                .replaceAll("\\s+", " ");
    }

    public record CategoryProduct(
            @JsonProperty("reference_id") Long referenceId,
            String sku,
            String name,
            String brand,
            String description,
            BigDecimal price,
            @JsonProperty("in_stock") boolean inStock,
            @JsonProperty("stock_quantity") int stockQuantity,
            @JsonProperty("warranty_months") int warrantyMonths) {

        static CategoryProduct from(ProductResponse product) {

            return new CategoryProduct(product.id(),
                    product.sku(),
                    product.name(),
                    product.brand(),
                    product.description(),
                    product.price(),
                    product.inStock(),
                    product.stockQuantity(),
                    product.warrantyMonths());
        }
    }
}
