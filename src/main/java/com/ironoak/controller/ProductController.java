package com.ironoak.controller;

import com.ironoak.dto.response.ProductResponse;
import com.ironoak.dto.response.ToolCategoryResponse;
import com.ironoak.services.ProductService;
import com.ironoak.services.ToolCategoryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public storefront catalog. Read-only. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ToolCategoryService toolCategoryService;

    public ProductController(ProductService productService, ToolCategoryService toolCategoryService) {
        this.productService = productService;
        this.toolCategoryService = toolCategoryService;
    }

    @GetMapping
    public PagedModel<ProductResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(name = "q", required = false) String search,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return new PagedModel<>(productService.list(category, search, pageable));
    }

    @GetMapping("/{id:\\d+}")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping("/sku/{sku}")
    public ProductResponse getBySku(@PathVariable String sku) {
        return productService.getBySku(sku);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return productService.categories();
    }

    /**
     * Products for a CNN class label, e.g. the result of an image classification.
     */
    @GetMapping("/by-vision/{modelLabel}")
    public List<ProductResponse> byVisionLabel(@PathVariable String modelLabel) {
        return productService.findByVisionLabel(modelLabel);
    }

    @GetMapping("/tool-categories")
    public List<ToolCategoryResponse> toolCategories() {
        return toolCategoryService.list();
    }

    @GetMapping("/tool-categories/{modelLabel}")
    public ToolCategoryResponse toolCategory(@PathVariable String modelLabel) {
        return toolCategoryService.getByModelLabel(modelLabel);
    }
}
