package com.ironoak.services;

import com.ironoak.domain.Product;
import com.ironoak.domain.ToolCategory;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.ProductMapper;
import com.ironoak.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final ProductMapper mapper;

    public ProductService(ProductRepository products,
            ProductMapper mapper) {
        this.products = products;
        this.mapper = mapper;
    }

    /**
     * Active products, optionally narrowed by storefront category or a name search.
     */
    public Page<ProductResponse> list(String category,
            String search,
            Pageable pageable) {
        if (search != null && !search.isBlank()) {

            return products.findByNameContainingIgnoreCaseAndIsActiveTrue(search.trim(),
                    pageable).map(mapper::toResponse);
        }
        if (category != null && !category.isBlank()) {
            return products.findByCategoryAndIsActiveTrue(category, pageable).map(mapper::toResponse);
        }
        return products.findByIsActiveTrue(pageable).map(mapper::toResponse);
    }

    /** Words that carry no meaning in a product search (English and Spanish). */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "the", "for", "of", "to", "in", "on", "with", "my", "me",
            "i", "need", "want", "looking", "some", "any", "do", "you", "have", "sell",
            "de", "la", "el", "los", "las", "un", "una", "para", "con", "y", "mi",
            "quiero", "necesito", "busco", "tienen", "venden");

    // this is making my AI agent dump , i think gpt-6 should be intelligence enough
    // avoid this and do not let it hamper, but it fails. had better help it out.
    /**
     * Active catalog search used by Piper (search_products, lookup_product_stock).
     *
     * The query is split into words; each word is looked for in the SKU, name,
     * brand, category, the tool category's display name and its synonyms, ignoring
     * case, accents and a plural "s". Products are ranked by how many words they
     * match, so "compressor for my garage" still finds AIR COMPRESSORS. category
     * and
     * visionName narrow the result and ignore case. The whole active catalog is
     * small (under a hundred rows), so ranking happens in memory.
     */
    public List<ProductResponse> search(String query,
            String category,
            String visionName,
            int limit) {

        List<String> words = searchWords(query);
        List<Scored> ranked = new ArrayList<>();

        for (Product product : products.findAll(activeIn(category, visionName))) {

            int score = words.isEmpty() ? 1 : matchedWords(product, words);

            if (score > 0) {
                ranked.add(new Scored(product, score));
            }
        }
        ranked.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(scored -> scored.product().getName()));

        return ranked.stream()
                .limit(limit)
                .map(scored -> mapper.toResponse(scored.product()))
                .toList();
    }

    private record Scored(Product product, int score) {
    }

    private static Specification<Product> activeIn(String category, String visionName) {

        return Specification.allOf(
                FilterSpecs.equal("isActive", true),
                !FilterSpecs.hasText(category) ? null
                        : (root, query, cb) -> cb.equal(cb.lower(root.get("category")),
                                category.trim().toLowerCase(Locale.ROOT)),
                !FilterSpecs.hasText(visionName) ? null
                        : (root, query, cb) -> cb.equal(cb.lower(root.get("visionName")),
                                visionName.trim().toLowerCase(Locale.ROOT)));
    }

    private static int matchedWords(Product product, List<String> words) {

        String text = searchableText(product);
        int matched = 0;

        for (String word : words) {

            if (text.contains(word) || text.contains(singular(word))) {
                matched++;
            }
        }
        return matched;
    }

    /** Everything a customer might call this product, normalized. */
    private static String searchableText(Product product) {

        StringBuilder text = new StringBuilder();

        for (String field : new String[] { product.getSku(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getVisionName() }) {

            if (field != null) {
                text.append(field).append(' ');
            }
        }
        ToolCategory toolCategory = product.getToolCategory();

        if (toolCategory != null) {

            text.append(toolCategory.getDisplayName()).append(' ');

            if (toolCategory.getSynonyms() != null) {
                toolCategory.getSynonyms().forEach(synonym -> text.append(synonym).append(' '));
            }
        }
        return normalize(text.toString());
    }

    private static List<String> searchWords(String query) {

        if (!FilterSpecs.hasText(query)) {
            return List.of();
        }
        return Arrays.stream(normalize(query).split(" "))
                .filter(word -> word.length() > 1)
                .filter(word -> !STOP_WORDS.contains(word))
                .distinct()
                .toList();
    }

    private static String singular(String word) {

        return word.length() > 3 && word.endsWith("s")
                ? word.substring(0, word.length() - 1)
                : word;
    }

    /** Lower case, no accents, letters and digits only, single spaces. */
    private static String normalize(String text) {

        String plain = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return plain.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .strip();
    }

    public ProductResponse getById(Long id) {
        return products.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public ProductResponse getBySku(String sku) {
        return products.findBySku(sku)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", sku));
    }

    public List<String> categories() {
        return products.findActiveCategories();
    }

    /**
     * Products matching a vision prediction label (a tool_category.model_label).
     */
    public List<ProductResponse> findByVisionLabel(String modelLabel) {

        return mapper.toResponses(products.findByVisionNameAndIsActiveTrue(modelLabel));
    }
}
