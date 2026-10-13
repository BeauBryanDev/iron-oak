package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.domain.enums.PricingType;
import com.ironoak.dto.response.MillingMachineResponse;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.dto.response.ServiceOfferingResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.ProductService;
import com.ironoak.services.ServiceCatalogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Read-only catalog tools exposed to Piper. */
@Component
public class CatalogTools {

    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 10;

    private final ProductService products;
    private final ServiceCatalogService catalog;

    public CatalogTools(ProductService products, ServiceCatalogService catalog) {

        this.products = products;
        this.catalog = catalog;
    }

    @Tool(name = "search_products", description = "Search active Iron Oak products. Put what the customer wants in query, in English, as plain words (for example 'air compressor', 'cordless drill', 'safety gloves'); words are matched against product names, categories and synonyms, so do not add category or vision_name unless you are sure of the exact value. Each result has reference_id: use it as reference_id with item_type PRODUCT in create_order. Stock and prices come from the catalog; never infer them. If nothing is found, retry with fewer or more general words before telling the customer. The limit defaults to 5 and cannot exceed 10.")
    public List<ProductSearchResult> searchProducts(
            @ToolParam(required = false, description = "Optional text matching SKU, name, brand, or category.") String query,
            @ToolParam(required = false, description = "Optional storefront category, one of: Hand Tools, Power Tools, Electrical, Plumbing, Paint & Finishes, Safety Equipment, Outdoor & Garden, Workshop & Storage, Building Materials, Fasteners & Hardware. Leave empty unless the customer named a category.") String category,
            @ToolParam(required = false, description = "Only the exact model label returned by identify_tool_from_image (for example wrench_ai). Never guess it; leave empty otherwise.") String vision_name,
            @ToolParam(required = false, description = "Number of results, default 5, maximum 10.") Integer limit) {

        int effectiveLimit = limit == null ? DEFAULT_SEARCH_LIMIT : limit;

        if (effectiveLimit < 1 || effectiveLimit > MAX_SEARCH_LIMIT) {
            throw new BusinessRuleException("limit must be between 1 and 10");
        }
        return products.search(query,
                category,
                vision_name,
                effectiveLimit).stream()
                .map(ProductSearchResult::from).toList();
    }

    @Tool(name = "get_product_details", description = "Get authoritative details for one active Iron Oak product by SKU (as returned by search_products), including reference_id for create_order, description, price, warranty, and current stock.")
    public ProductDetailsResult getProductDetails(
            @ToolParam(description = "The exact product SKU.") String sku) {

        return ProductDetailsResult.from(products.getBySku(requiredText(sku, "sku")));
    }

    @Tool(name = "list_machines", description = "List active milling machines, optionally narrowed to an exact model code. Return catalog specifications, price and reference_id (use it with item_type MACHINE in create_order; machine shipping is quoted later by staff); machine inventory is not ordinary product stock.")
    public List<MachineResult> listMachines(
            @ToolParam(required = false, description = "Optional exact milling-machine model code.") String model_code) {

        List<MillingMachineResponse> machines = hasText(model_code)
                ? List.of(catalog.getMachine(model_code.trim()))
                : catalog.listMachines();

        return machines.stream().map(MachineResult::from).toList();
    }

    @Tool(name = "list_services", description = "List active Iron Oak technical services, optionally narrowed to an exact service code. Explain FIXED, HOURLY, and QUOTE pricing from the returned fields; never invent a quote price. reference_id is the id to use with item_type SERVICE in create_order (HOURLY services also need estimatedHours; QUOTE services cannot be ordered).")
    public List<ServiceResult> listServices(
            @ToolParam(required = false, description = "Optional exact service code.") String code) {

        List<ServiceOfferingResponse> services = hasText(code)
                ? List.of(catalog.getService(code.trim()))
                : catalog.listServices();

        return services.stream().map(ServiceResult::from).toList();
    }

    private static String requiredText(String value, String field) {

        if (!hasText(value)) {
            throw new BusinessRuleException(field + " is required");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {

        return value != null && !value.isBlank();
    }

    public record ProductSearchResult(@JsonProperty("reference_id") Long referenceId,
            String sku,
            String name,
            String brand,
            String category,
            BigDecimal price,
            @JsonProperty("stock_quantity") int stockQuantity) {

        static ProductSearchResult from(ProductResponse product) {

            return new ProductSearchResult(product.id(),
                    product.sku(),
                    product.name(),
                    product.brand(),
                    product.category(),
                    product.price(),
                    product.stockQuantity());
        }
    }

    public record ProductDetailsResult(@JsonProperty("reference_id") Long referenceId,
            String sku,
            String name,
            String description,
            String brand,
            String category,
            BigDecimal price,
            @JsonProperty("warranty_months") int warrantyMonths,
            @JsonProperty("stock_quantity") int stockQuantity) {

        static ProductDetailsResult from(ProductResponse product) {

            return new ProductDetailsResult(product.id(),
                    product.sku(),
                    product.name(),
                    product.description(),
                    product.brand(),
                    product.category(),
                    product.price(),
                    product.warrantyMonths(),
                    product.stockQuantity());
        }
    }

    public record MachineResult(@JsonProperty("reference_id") Long referenceId,
            @JsonProperty("model_code") String modelCode,
            String name,
            String description,
            @JsonProperty("power_kw") BigDecimal powerKw,
            @JsonProperty("spindle_min_rpm") int spindleMinRpm,
            @JsonProperty("spindle_max_rpm") int spindleMaxRpm,
            @JsonProperty("table_length_mm") int tableLengthMm,
            @JsonProperty("table_width_mm") int tableWidthMm, BigDecimal price,
            @JsonProperty("warranty_months") int warrantyMonths) {

        static MachineResult from(MillingMachineResponse machine) {

            return new MachineResult(machine.id(),
                    machine.modelCode(),
                    machine.name(),
                    machine.description(),
                    machine.powerKw(),
                    machine.spindleMinRpm(),
                    machine.spindleMaxRpm(),
                    machine.tableLengthMm(),
                    machine.tableWidthMm(),
                    machine.price(),
                    machine.warrantyMonths());
        }
    }

    public record ServiceResult(@JsonProperty("reference_id") Long referenceId,
            String code,
            String name,
            String description,
            PricingType pricingType,
            @JsonProperty("fixed_price") BigDecimal fixedPrice,
            @JsonProperty("hourly_rate") BigDecimal hourlyRate,
            @JsonProperty("estimated_min_hours") BigDecimal estimatedMinHours,
            @JsonProperty("estimated_max_hours") BigDecimal estimatedMaxHours,
            @JsonProperty("price_unit") String priceUnit) {

        static ServiceResult from(ServiceOfferingResponse service) {

            return new ServiceResult(service.id(),
                    service.code(),
                    service.name(),
                    service.description(),
                    service.pricingType(),
                    service.fixedPrice(),
                    service.hourlyRate(),
                    service.estimatedMinHours(),
                    service.estimatedMaxHours(),
                    service.priceUnit());
        }
    }
}
