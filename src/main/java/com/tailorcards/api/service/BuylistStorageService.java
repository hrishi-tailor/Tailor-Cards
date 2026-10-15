package com.tailorcards.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.tailorcards.api.listing.ListingImageSanitizer;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

@Service
public class BuylistStorageService {

    private static final Logger log = LoggerFactory.getLogger(BuylistStorageService.class);

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    @Value("${supabase.bucket:buylist-images}")
    private String supabaseBucket;

    @Value("${buylist.upload-dir:uploads/buylist}")
    private String uploadDir;

    private final HttpClient httpClient;
    private final ListingImageSanitizer sanitizer = new ListingImageSanitizer();

    public BuylistStorageService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public BuylistStorageService(HttpClient httpClient, String supabaseUrl, String supabaseKey, String supabaseBucket, String uploadDir) {
        this.httpClient = httpClient;
        this.supabaseUrl = supabaseUrl;
        this.supabaseKey = supabaseKey;
        this.supabaseBucket = supabaseBucket;
        this.uploadDir = uploadDir;
    }

    /**
     * Stores an uploaded image. Whatever the client says the file is, it must be a JPEG, PNG or WebP by its magic
     * bytes, within the size limit and a sane pixel count; metadata (location, camera) is stripped, and the stored
     * name and content type come from the detected format, never from the upload.
     */
    public String storeFile(MultipartFile file) {
        validateSize(file);
        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file contents: " + e.getMessage(), e);
        }
        ListingImageSanitizer.SanitizedImage image = sanitizer.sanitize(raw);
        byte[] bytes = image.bytes();
        String contentType = image.type().mediaType();
        String uniqueFilename = UUID.randomUUID() + switch (image.type()) {
            case JPEG -> ".jpg";
            case PNG -> ".png";
            case WEBP -> ".webp";
        };

        if (isSupabaseConfigured()) {
            try {
                String supabasePublicUrl = uploadToSupabase(contentType, bytes, uniqueFilename);
                if (supabasePublicUrl != null) {
                    log.info("Successfully uploaded image to Supabase Storage: {}", supabasePublicUrl);
                    return supabasePublicUrl;
                }
                log.warn("Supabase Storage did not return a valid URL, falling back to local storage");
            } catch (Throwable t) {
                log.warn("Supabase Storage upload failed ({}: {}), falling back to local storage",
                        t.getClass().getSimpleName(), t.getMessage());
            }
        }

        return storeLocally(bytes, uniqueFilename);
    }

    private void validateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE,
                    "Images can be at most 10 MB.");
        }
    }

    private boolean isSupabaseConfigured() {
        return supabaseUrl != null && !supabaseUrl.isBlank() && supabaseKey != null && !supabaseKey.isBlank();
    }

    private String uploadToSupabase(String contentType, byte[] bytes, String filename) throws Exception {
        String baseUrl = supabaseUrl.replaceAll("/+$", "");
        String uploadEndpoint = baseUrl + "/storage/v1/object/" + supabaseBucket + "/" + filename;

        String mimeType = (contentType != null && !contentType.isBlank()) ? contentType : "image/jpeg";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + supabaseKey.trim())
                .header("apikey", supabaseKey.trim())
                .header("Content-Type", mimeType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return baseUrl + "/storage/v1/object/public/" + supabaseBucket + "/" + filename;
        }

        log.warn("Supabase returned HTTP {} when uploading {}: {}", response.statusCode(), filename, response.body());
        return null;
    }

    private String storeLocally(byte[] bytes, String filename) {
        try {
            Path targetDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(targetDirectory)) {
                Files.createDirectories(targetDirectory);
            }

            Path targetPath = targetDirectory.resolve(filename).normalize();
            Files.write(targetPath, bytes);

            log.info("Stored file locally at: {}", targetPath);
            return "/uploads/buylist/" + filename;
        } catch (IOException e) {
            log.error("Failed to store file locally in {}: {}", uploadDir, e.getMessage(), e);
            throw new RuntimeException("Failed to store file locally: " + e.getMessage(), e);
        }
    }

}
