package com.tailorcards.api.mapper;

import com.tailorcards.api.dto.CategoryRequest;
import com.tailorcards.api.dto.CategoryResponse;
import com.tailorcards.api.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        if (category == null) {
            return null;
        }
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }

    public Category toEntity(CategoryRequest request) {
        if (request == null) {
            return null;
        }
        return Category.builder()
                .name(request.name())
                .description(request.description())
                .build();
    }

    public void updateEntity(Category category, CategoryRequest request) {
        if (category == null || request == null) {
            return;
        }
        category.setName(request.name());
        category.setDescription(request.description());
    }
}
