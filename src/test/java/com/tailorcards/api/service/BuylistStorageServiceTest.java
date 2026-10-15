package com.tailorcards.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /** A real image of the given format (ImageIO), so uploads pass the magic-byte and structure checks. */
    static byte[] image(String format) throws java.io.IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** A 1x1 lossless WebP (ImageIO can't write WebP). */
    static final byte[] WEBP = java.util.Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

    @Test
    void storeFile_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void storeFile_fileExceeding10MB_isRejectedAs413() {
        MockMultipartFile file = new MockMultipartFile("file", "large.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]);

        org.springframework.web.server.ResponseStatusException ex =
                assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> storageService.storeFile(file));
        assertEquals(413, ex.getStatusCode().value());
    }

    @Test
    void storeFile_notAnImage_isRejectedWhateverItClaims() {
        // A PDF, a shell script and an HTML page dressed up as images by name and declared type
        for (MockMultipartFile file : java.util.List.of(
                new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.7 content".getBytes()),
                new MockMultipartFile("file", "script.jpg", "image/jpeg", "#!/bin/sh\necho hello".getBytes()),
                new MockMultipartFile("file", "card.png", "image/png", "<html><script>alert(1)</script></html>".getBytes()),
                new MockMultipartFile("file", "anim.gif", "image/gif", "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000".getBytes()))) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storageService.storeFile(file));
            assertTrue(ex.getMessage().contains("JPEG, PNG or WebP"), file.getOriginalFilename());
        }
    }

    @Test
    void storeFile_pixelBomb_isRejectedBeforeDecoding() throws Exception {
        byte[] png = image("png");
        // Rewrite IHDR to claim 100,000 x 100,000 pixels (10 gigapixels)
        java.nio.ByteBuffer.wrap(png, 16, 8).putInt(100_000).putInt(100_000);
        MockMultipartFile file = new MockMultipartFile("file", "bomb.png", "image/png", png);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storageService.storeFile(file));
        assertTrue(ex.getMessage().contains("too large"));
    }

    @Test
    void storeFile_validImage_storesLocallyWithTheDetectedType() throws Exception {
        String png = storageService.storeFile(new MockMultipartFile("file", "card.png", "image/png", image("png")));
        assertTrue(png.startsWith("/uploads/buylist/"));
        assertTrue(png.endsWith(".png"));

        // A PNG uploaded as "card.jpg" / image/jpeg is stored as what it really is
        String disguised = storageService.storeFile(new MockMultipartFile("file", "card.jpg", "image/jpeg", image("png")));
        assertTrue(disguised.endsWith(".png"));
    }

    @Test
    void storeFile_whenSupabaseThrowsException_fallsBackToLocalStorage() throws Exception {
        HttpClient failingHttpClient = mock(HttpClient.class);
        when(failingHttpClient.send(any(HttpRequest.class), any()))
                .thenThrow(new java.net.ConnectException("Connection refused to Supabase"));

        BuylistStorageService serviceWithSupabase = new BuylistStorageService(
                failingHttpClient,
                "https://example.supabase.co",
                "secret-api-key",
                "buylist-images",
                tempDir.resolve("fallback-test").toString()
        );

        String result = serviceWithSupabase.storeFile(new MockMultipartFile("file", "charizard.jpg", "image/jpeg", image("jpg")));

        assertNotNull(result);
        assertTrue(result.startsWith("/uploads/buylist/"));
        assertTrue(result.endsWith(".jpg"));
    }

    @Test
    void storeFile_whenSupabaseReturns502_fallsBackToLocalStorage() throws Exception {
        HttpClient badGatewayHttpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(502);
        doReturn(mockResponse).when(badGatewayHttpClient).send(any(HttpRequest.class), any());

        BuylistStorageService serviceWithSupabase = new BuylistStorageService(
                badGatewayHttpClient,
                "https://example.supabase.co",
                "secret-api-key",
                "buylist-images",
                tempDir.resolve("fallback-502-test").toString()
        );

        String result = serviceWithSupabase.storeFile(new MockMultipartFile("file", "pikachu.webp", "image/webp", WEBP));

        assertNotNull(result);
        assertTrue(result.startsWith("/uploads/buylist/"));
        assertTrue(result.endsWith(".webp"));
    }
}
