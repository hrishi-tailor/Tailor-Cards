package com.tailorcards.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Public image upload validation")
class UploadValidationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static byte[] jpegWithGps() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", out);
        byte[] jpeg = out.toByteArray();
        // Insert an APP1 Exif segment carrying a GPS marker string right after SOI
        byte[] exif = "Exif\0\0GPSLatitude 43.6532 GPSLongitude -79.3832".getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream withExif = new ByteArrayOutputStream();
        withExif.write(jpeg, 0, 2);
        withExif.write(0xFF);
        withExif.write(0xE1);
        withExif.write((exif.length + 2) >> 8);
        withExif.write((exif.length + 2) & 0xFF);
        withExif.write(exif);
        withExif.write(jpeg, 2, jpeg.length - 2);
        return withExif.toByteArray();
    }

    @Test
    @DisplayName("Only real JPEG/PNG/WebP is accepted, by content; metadata such as GPS is stripped before storing")
    void publicUpload() throws Exception {
        mvc.perform(multipart("/api/buylist/upload").file(new MockMultipartFile("file", "card.jpg", "image/jpeg",
                        "<html><body><script>alert(document.cookie)</script></body></html>".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/buylist/upload").file(new MockMultipartFile("file", "card.svg", "image/svg+xml",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes())))
                .andExpect(status().isBadRequest());

        byte[] photo = jpegWithGps();
        assertThat(new String(photo, StandardCharsets.ISO_8859_1)).contains("GPSLatitude");
        String body = mvc.perform(multipart("/api/buylist/upload").file(new MockMultipartFile("file", "card.png", "image/png", photo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = new ObjectMapper().readTree(body).path("url").asText();
        assertThat(url).startsWith("/uploads/buylist/").endsWith(".jpg"); // stored as what it is, not what it claimed

        byte[] stored = Files.readAllBytes(Path.of(url.substring(1)));
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("GPSLatitude").doesNotContain("Exif");
        Files.deleteIfExists(Path.of(url.substring(1)));
    }
}
