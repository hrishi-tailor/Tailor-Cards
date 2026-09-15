package com.tailorcards.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class BuylistStorageService {

    private static final Logger log = LoggerFactory.getLogger(BuylistStorageService.class);

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp"
    );

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    @Value("${supabase.bucket:buylist-images}")
    private String supabaseBucket;

    @Value("${buylist.upload-dir:uploads/buylist}")
    private String uploadDir;

    private final HttpClient httpClient;

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

    public String storeFile(MultipartFile file) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
        String extension = extractExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID() + extension;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file contents: " + e.getMessage(), e);
        }

        if (isSupabaseConfigured()) {
            try {
                String supabasePublicUrl = uploadToSupabase(file.getContentType(), bytes, uniqueFilename);
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

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds the 10MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid file type: " + contentType + ". Only JPEG, PNG, and WEBP images are allowed");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid file extension: " + extension + ". Only .jpg, .jpeg, .png, and .webp are allowed");
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

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }
}
