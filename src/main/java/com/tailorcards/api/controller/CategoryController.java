package com.tailorcards.api.controller;

import com.tailorcards.api.dto.CategoryRequest;
import com.tailorcards.api.dto.CategoryResponse;
import com.tailorcards.api.service.CategoryService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Endpoints for managing card categories (Singles, Sealed, Slabs)")
public class CategoryController {

    private final CategoryService categoryService;
    private final com.tailorcards.api.audit.AuditLog audit;

    public CategoryController(CategoryService categoryService, com.tailorcards.api.audit.AuditLog audit) {
        this.audit = audit;
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List all categories", description = "Retrieve all card categories and taxonomies")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Retrieve a specific category by ID")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping
    @Operation(summary = "Create category (Admin)", description = "Create a new category. Requires HTTP Basic authentication with ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = categoryService.createCategory(request);
        audit.record("CATEGORY_CREATE", "CATEGORY", created.id(), null, created);
        URI location = URI.create("/api/categories/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category (Admin)", description = "Update category details. Requires HTTP Basic authentication with ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        String before = audit.snapshotOf(() -> categoryService.getCategoryById(id));
        CategoryResponse after = categoryService.updateCategory(id, request);
        audit.record("CATEGORY_UPDATE", "CATEGORY", id, before, after);
        return ResponseEntity.ok(after);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category (Admin)", description = "Remove a category. Requires HTTP Basic authentication with ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        String before = audit.snapshotOf(() -> categoryService.getCategoryById(id));
        categoryService.deleteCategory(id);
        audit.record("CATEGORY_DELETE", "CATEGORY", id, before, null);
        return ResponseEntity.noContent().build();
    }
}
