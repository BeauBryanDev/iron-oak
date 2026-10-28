package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.domain.enums.PricingType;
import com.ironoak.dto.response.MillingMachineResponse;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.dto.response.ServiceOfferingResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.services.ProductService;
import com.ironoak.services.ServiceCatalogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

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

    @Tool(name = "search_products", description = "Search active Iron Oak products by plain English words (e.g. 'air compressor', 'cordless drill'), matched against names, categories and synonyms. Results carry price, stock and reference_id (item_type PRODUCT in create_order). If nothing is found, retry with fewer or more general words.")
    public List<ProductSearchResult> searchProducts(
            @ToolParam(required = false, description = "Words to match: SKU, name, brand or category.") String query,
            @ToolParam(required = false, description = "Exact storefront category; leave empty unless the customer named one.") String category,
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

    @Tool(name = "list_machines", description = "List the milling machines Iron & Oak itself sells (a short list of its own model codes such as VMC-650), with specs, price and reference_id for create_order (item_type MACHINE; freight is quoted by staff). Models from other makers' catalogs (AVIA VMC, VARIO, X-5, WELE HB1212/HB1416/HB1620 and similar) are not here: look those up with search_knowledge.")
    public List<MachineResult> listMachines(
            @ToolParam(required = false, description = "Optional exact model code of one of Iron & Oak's own machines.") String model_code) {

        if (!hasText(model_code)) {
            return catalog.listMachines().stream().map(MachineResult::from).toList();
        }
        try {
            return List.of(MachineResult.from(catalog.getMachine(model_code.trim())));
        } catch (ResourceNotFoundException e) {
            // tells the model where to look next instead of a bare "not found"
            String own = catalog.listMachines().stream()
                    .map(MillingMachineResponse::modelCode)
                    .collect(Collectors.joining(", "));
            throw new BusinessRuleException(model_code.trim() + " is not one of the machines Iron & Oak sells ("
                    + own + "). For other models call search_knowledge with namespaces iron_oak and machine_operations.");
        }
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
