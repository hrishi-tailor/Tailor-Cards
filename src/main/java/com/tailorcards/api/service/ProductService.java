package com.tailorcards.api.service;

import com.tailorcards.api.dto.ProductRequest;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.exception.ResourceNotFoundException;
import com.tailorcards.api.exception.StockConflictException;
import com.tailorcards.api.mapper.ProductMapper;
import com.tailorcards.api.repository.CategoryRepository;
import com.tailorcards.api.repository.ProductRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ProductImageService productImages;

    @Autowired
    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductMapper productMapper,
            ProductImageService productImages
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
        this.productImages = productImages;
    }

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          ProductMapper productMapper) {
        this(productRepository, categoryRepository, productMapper, null);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return productMapper.toResponse(product);
    }

    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        Product product = productMapper.toEntity(request, category);
        // No picture given: default to the official card image when the card can be matched
        if ((product.getImageUrl() == null || product.getImageUrl().isBlank()) && productImages != null) {
            productImages.officialImage(product).ifPresent(product::setImageUrl);
        }
        Product savedProduct = productRepository.save(product);
        return productMapper.toResponse(savedProduct);
    }

    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));

        productMapper.updateEntity(product, request, category);
        Product updatedProduct = productRepository.save(product);
        return productMapper.toResponse(updatedProduct);
    }

    /** Appends the seller's photos (already stored URLs) after the existing ones. */
    public ProductResponse addPhotos(Long id, List<String> urls) {
        Product product = find(id);
        List<String> photos = product.getPhotoUrls();
        for (String url : urls) {
            if (!photos.contains(url)) {
                photos.add(url);
            }
        }
        if (photos.size() > ProductRequest.MAX_PHOTOS) {
            throw new IllegalArgumentException("A product can have at most " + ProductRequest.MAX_PHOTOS + " photos.");
        }
        return productMapper.toResponse(productRepository.save(product));
    }

    public ProductResponse removePhoto(Long id, int index) {
        Product product = find(id);
        if (index < 0 || index >= product.getPhotoUrls().size()) {
            throw new ResourceNotFoundException("Photo " + index + " not found on product " + id);
        }
        product.getPhotoUrls().remove(index);
        return productMapper.toResponse(productRepository.save(product));
    }

    /**
     * Makes the official card image the default picture. A previous picture that was the seller's own
     * photo moves to the front of the photos so it stays visible.
     */
    public ProductResponse useOfficialImage(Long id) {
        Product product = find(id);
        if (!applyOfficialImage(product)) {
            throw new ResourceNotFoundException("No official card image found for product " + id
                    + ". Link the card (card id) or set its set and card number.");
        }
        return productMapper.toResponse(productRepository.save(product));
    }

    /** {@link #useOfficialImage} for every product that doesn't show an official image yet. */
    public OfficialImageBackfill useOfficialImagesForAll() {
        int updated = 0;
        List<String> unmatched = new ArrayList<>();
        for (Product product : productRepository.findAll()) {
            if (ProductImageService.isOfficialImage(product.getImageUrl())) {
                continue;
            }
            if (applyOfficialImage(product)) {
                productRepository.save(product);
                updated++;
            } else {
                unmatched.add(product.getName());
            }
        }
        return new OfficialImageBackfill(updated, unmatched);
    }

    public record OfficialImageBackfill(int updated, List<String> unmatched) {}

    private boolean applyOfficialImage(Product product) {
        if (productImages == null) {
            return false;
        }
        return productImages.officialImage(product).map(official -> {
            String previous = product.getImageUrl();
            if (previous != null && !previous.isBlank() && !previous.equals(official)
                    && !ProductImageService.isOfficialImage(previous) && !product.getPhotoUrls().contains(previous)) {
                product.getPhotoUrls().add(0, previous);
            }
            product.setImageUrl(official);
            return true;
        }).orElse(false);
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    public ProductResponse decrementStock(Long id, int quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        try {
            product.decrementStock(quantity);
            Product updatedProduct = productRepository.save(product);
            return productMapper.toResponse(updatedProduct);
        } catch (OptimisticLockException | OptimisticLockingFailureException e) {
            throw new StockConflictException("item no longer available at requested quantity", e);
        }
    }
}
