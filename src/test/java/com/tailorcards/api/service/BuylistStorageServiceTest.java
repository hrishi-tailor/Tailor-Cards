package com.tailorcards.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.net.http.HttpClient;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BuylistStorageServiceTest {

    @TempDir
    Path tempDir;

    private BuylistStorageService storageService;

    @BeforeEach
    void setUp() {
        HttpClient mockHttpClient = mock(HttpClient.class);
        storageService = new BuylistStorageService(
                mockHttpClient,
                "",
                "",
                "buylist-images",
                tempDir.toString()
        );
    }

    @Test
    void storeFile_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                new byte[0]
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void storeFile_fileExceeding10MB_throwsIllegalArgumentException() {
        byte[] largeBytes = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                largeBytes
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("10MB"));
    }

    @Test
    void storeFile_invalidContentType_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.pdf",
                "application/pdf",
                "content".getBytes()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("Invalid file type"));
    }

    @Test
    void storeFile_invalidExtension_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.sh",
                "image/jpeg",
                "echo hello".getBytes()
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("extension"));
    }

    @Test
    void storeFile_validImage_storesLocallyAndReturnsUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "card.png",
                "image/png",
                "valid-image-data".getBytes()
        );

        String result = storageService.storeFile(file);

        assertNotNull(result);
        assertTrue(result.startsWith("/uploads/buylist/"));
        assertTrue(result.endsWith(".png"));
    }
}
