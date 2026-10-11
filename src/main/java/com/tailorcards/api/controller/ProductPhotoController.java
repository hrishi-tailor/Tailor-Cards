package com.tailorcards.api.controller;

import com.tailorcards.api.buylistchat.SanitizedPhotoFile;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.listing.ListingImageSanitizer;
import com.tailorcards.api.service.BuylistStorageService;
import com.tailorcards.api.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Product pictures (ADMIN; POST and DELETE under /api/** require the ADMIN role): the seller's own
 * photos, and the official card image as the default picture.
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Product photos", description = "Seller photos and official card images for catalog products")
public class ProductPhotoController {

    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;

    private final ProductService productService;
    private final BuylistStorageService storage;
    private final ListingImageSanitizer sanitizer;

    public ProductPhotoController(ProductService productService, BuylistStorageService storage,
                                  ListingImageSanitizer sanitizer) {
        this.productService = productService;
        this.storage = storage;
        this.sanitizer = sanitizer;
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a product photo (Admin)", description = "Stores one photo (JPEG/PNG/WebP, 10 MB, location data stripped) and returns its URL, for use in photoUrls.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<Map<String, String>> uploadPhoto(@RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(Map.of("url", store(file)));
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add photos to a product (Admin)", description = "Stores the photos and appends them after the product's existing photos.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<ProductResponse> addPhotos(@PathVariable Long id,
                                                     @RequestPart("files") List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one photo.");
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(store(file));
        }
        return ResponseEntity.ok(productService.addPhotos(id, urls));
    }

    @DeleteMapping("/{id}/photos/{index}")
    @Operation(summary = "Remove a product photo (Admin)", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<ProductResponse> removePhoto(@PathVariable Long id, @PathVariable int index) {
        return ResponseEntity.ok(productService.removePhoto(id, index));
    }

    @PostMapping("/{id}/official-image")
    @Operation(summary = "Use the official card image (Admin)", description = "Sets the default picture to the card's official image (by linked card id, or set and number). A previous seller photo moves into the photos.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<ProductResponse> useOfficialImage(@PathVariable Long id) {
        return ResponseEntity.ok(productService.useOfficialImage(id));
    }

    @PostMapping("/official-images")
    @Operation(summary = "Use official card images for all products (Admin)", description = "Applies the official image to every product that doesn't show one yet; returns how many changed and which couldn't be matched.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<ProductService.OfficialImageBackfill> useOfficialImagesForAll() {
        return ResponseEntity.ok(productService.useOfficialImagesForAll());
    }

    private String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("Photos must be JPEG, PNG or WebP and at most 10 MB.");
        }
        return storage.storeFile(new SanitizedPhotoFile(sanitizer.sanitize(file.getBytes())));
    }
}
