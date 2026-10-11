package com.tailorcards.api.controller;

import com.tailorcards.api.listing.ListingGeneratorService;
import com.tailorcards.api.listing.dto.MatchStatus;
import com.tailorcards.api.listing.dto.ListingDraftResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Admin Listing Generator Controller Tests")
class AdminListingGeneratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingGeneratorService listingGeneratorService;

    private final MockMultipartFile photo = new MockMultipartFile("images", "front.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @Test
    @DisplayName("Anonymous requests are rejected with 401 and never reach the service")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/admin/listing-generator/draft").file(photo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/listing-generator/market-reference").param("cardId", "base1-4"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(listingGeneratorService);
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role is forbidden (403) from drafting and market references")
    void demoIsForbidden() throws Exception {
        mockMvc.perform(multipart("/api/admin/listing-generator/draft").file(photo))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/listing-generator/market-reference").param("cardId", "base1-4"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(listingGeneratorService);
    }

    @Test
    @WithMockUser(username = "store-admin", roles = "ADMIN")
    @DisplayName("ADMIN gets the draft; the service is keyed by the admin's username")
    void adminGetsDraft() throws Exception {
        when(listingGeneratorService.generateDraft(eq("store-admin"), anyList(), eq("front only")))
                .thenReturn(new ListingDraftResponse(null, MatchStatus.NONE, null, List.of()));

        mockMvc.perform(multipart("/api/admin/listing-generator/draft").file(photo).param("note", "front only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("NONE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Upstream failure surfaces as 503 with a clear message, not 500")
    void upstreamFailureIs503() throws Exception {
        when(listingGeneratorService.generateDraft(any(), anyList(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is unavailable right now."));

        mockMvc.perform(multipart("/api/admin/listing-generator/draft").file(photo))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The AI service is unavailable right now."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Rate limit and daily cap surface as 429")
    void rateLimitIs429() throws Exception {
        when(listingGeneratorService.generateDraft(any(), anyList(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily listing draft limit reached (100 per day)."));

        mockMvc.perform(multipart("/api/admin/listing-generator/draft").file(photo))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("Daily listing draft limit")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Rejected uploads surface as 400")
    void badUploadIs400() throws Exception {
        when(listingGeneratorService.generateDraft(any(), anyList(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported image. Upload a JPEG, PNG or WebP photo."));

        mockMvc.perform(multipart("/api/admin/listing-generator/draft").file(photo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("JPEG, PNG or WebP")));
    }
}
