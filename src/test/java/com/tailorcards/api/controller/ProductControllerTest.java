package com.tailorcards.api.controller;

import com.tailorcards.api.dto.CategoryResponse;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private ProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductController(productService);
    }

    @Test
    void pageableDefault_annotation_shouldDefaultToSize100() throws Exception {
        Method getProductsMethod = ProductController.class.getMethod("getProducts", Pageable.class);
        Parameter pageableParam = getProductsMethod.getParameters()[0];
        PageableDefault annotation = pageableParam.getAnnotation(PageableDefault.class);

        assertNotNull(annotation, "@PageableDefault annotation must be present on getProducts pageable parameter");
        assertEquals(100, annotation.size(), "Default page size must be 100 to allow loading all items");
    }

    @Test
    void getProducts_withSize100_shouldReturnAllProducts() {
        CategoryResponse category = new CategoryResponse(1L, "Singles", "Singles description");
        List<ProductResponse> products = new ArrayList<>();
        for (int i = 1; i <= 68; i++) {
            products.add(new ProductResponse(
                    (long) i,
                    "Product " + i,
                    "Description " + i,
                    BigDecimal.valueOf(99.00),
                    "/images/item" + i + ".jpg",
                    0,
                    category,
                    String.valueOf(i),
                    "Set",
                    "NM",
                    null,
                    "SOLD"
            ));
        }

        PageRequest pageRequest = PageRequest.of(0, 100);
        when(productService.getProducts(any(Pageable.class)))
                .thenReturn(new PageImpl<>(products, pageRequest, 68));

        ResponseEntity<Page<ProductResponse>> response = controller.getProducts(pageRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(68, response.getBody().getContent().size());
        assertEquals("SOLD", response.getBody().getContent().get(0).status());
        verify(productService).getProducts(pageRequest);
    }
}
