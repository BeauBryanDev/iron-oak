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

    @Tool(name = "search_products_by_category", description = "List the active Iron Oak products of one "
            + "storefront category, with price and stock. Use it to recommend products after giving advice: "
            + "once you have explained a repair or job (often from search_knowledge), pick the category that "
            + "fits and suggest the few products the customer actually needs for it, with their price. "
            + "Typical matches: faucets, pipes, leaks, valves, drains -> Plumbing; wiring, sockets, testing "
            + "circuits, lighting -> Electrical; painting, sealing, caulking -> Paint & Finishes; drilling, "
            + "cutting, grinding, sanding -> Power Tools; general repairs, measuring, fixing -> Hand Tools; "
            + "screws, nails, nuts, chains -> Fasteners & Hardware; concrete, plaster, masonry -> Building "
            + "Materials; protection for any job -> Safety Equipment; yard and garden work -> Outdoor & "
            + "Garden; storage, ladders, workshop gear -> Workshop & Storage. Each result has reference_id "
            + "for create_order (item_type PRODUCT). Prices and stock come from the database; never invent "
            + "them, and do not recommend products the tool did not return.")
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
