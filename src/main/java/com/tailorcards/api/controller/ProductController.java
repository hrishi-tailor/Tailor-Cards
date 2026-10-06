package com.tailorcards.api.controller;

import com.tailorcards.api.dto.PriceHistoryResponse;
import com.tailorcards.api.dto.ProductRequest;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.service.PriceHistoryService;
import com.tailorcards.api.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Endpoints for managing and querying the collectible trading card catalog")
public class ProductController {

    private final ProductService productService;
    private final PriceHistoryService priceHistoryService;

    public ProductController(ProductService productService, PriceHistoryService priceHistoryService) {
        this.productService = productService;
        this.priceHistoryService = priceHistoryService;
    }

    @GetMapping
    @Operation(summary = "List catalog products", description = "Retrieve a paginated list of collectible products with category details")
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @PageableDefault(size = 100, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(productService.getProducts(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Retrieve single product details including stock and category")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/{id}/price-history")
    @Operation(
        summary = "Get historical card price trends",
        description = "Retrieve historical price points, period high/low, and market trends for a card over 1M, 3M, or 1Y intervals."
    )
    public ResponseEntity<PriceHistoryResponse> getPriceHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "3M") String range
    ) {
        return ResponseEntity.ok(priceHistoryService.getPriceHistory(id, range));
    }

    @PostMapping
    @Operation(summary = "Create product (Admin)", description = "Create a new catalog product. Requires HTTP Basic authentication with ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        URI location = URI.create("/api/products/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product (Admin)", description = "Update an existing catalog product. Requires HTTP Basic authentication with ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product (Admin)", description = "Remove a product from inventory. Requires HTTP Basic authentication with ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
