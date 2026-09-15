package com.tailorcards.api.mapper;

import com.tailorcards.api.dto.CategoryResponse;
import com.tailorcards.api.dto.ProductRequest;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    private final CategoryMapper categoryMapper;

    public ProductMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public ProductResponse toResponse(Product product) {
        if (product == null) {
            return null;
        }
        CategoryResponse categoryResponse = product.getCategory() != null
                ? categoryMapper.toResponse(product.getCategory())
                : null;

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl(),
                product.getStock(),
                categoryResponse,
                product.getCardNumber(),
                product.getSet(),
                product.getCondition(),
                product.getGrading(),
                product.getStatus() != null ? product.getStatus() : "AVAILABLE"
        );
    }

    public Product toEntity(ProductRequest request, Category category) {
        if (request == null) {
            return null;
        }
        String status = request.status() != null && !request.status().isBlank()
                ? request.status()
                : "AVAILABLE";

        return Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .stock(request.stock())
                .category(category)
                .cardNumber(request.cardNumber())
                .set(request.set())
                .condition(request.condition())
                .grading(request.grading())
                .status(status)
                .build();
    }

    public void updateEntity(Product product, ProductRequest request, Category category) {
        if (product == null || request == null) {
            return;
        }
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setImageUrl(request.imageUrl());
        product.setStock(request.stock());
        if (category != null) {
            product.setCategory(category);
        }
        product.setCardNumber(request.cardNumber());
        product.setSet(request.set());
        product.setCondition(request.condition());
        product.setGrading(request.grading());
        if (request.status() != null && !request.status().isBlank()) {
            product.setStatus(request.status());
        }
    }
}
