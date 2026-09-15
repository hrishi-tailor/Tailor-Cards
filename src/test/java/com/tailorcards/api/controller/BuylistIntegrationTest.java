package com.tailorcards.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BuylistIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicEndpoints_shouldBeAccessibleWithoutAuth() throws Exception {
        // 1. Upload an image (public)
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "card.jpg",
                "image/jpeg",
                "fake-card-data".getBytes()
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/buylist/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", notNullValue()))
                .andReturn();

        String uploadUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("url").asText();

        // 2. Submit a buylist item (public)
        String submitJson = """
                {
                    "customerEmail": "pokemonfan@example.com",
                    "customerName": "Red",
                    "cardName": "Pikachu Illustrator",
                    "cardSet": "Promo",
                    "askingPrice": 250000.00,
                    "additionalComments": "One of a kind",
                    "imageUrls": ["%s"]
                }
                """.formatted(uploadUrl);

        MvcResult submitResult = mockMvc.perform(post("/api/buylist/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.trackingToken", notNullValue()))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn();

        String trackingToken = objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("trackingToken").asText();

        // 3. Track submission (public)
        mockMvc.perform(get("/api/buylist/track/" + trackingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingToken", is(trackingToken)))
                .andExpect(jsonPath("$.cardName", is("Pikachu Illustrator")));

        // 4. Customer sends message (public)
        String customerMsgJson = """
                {
                    "message": "Just checking if you received my submission!"
                }
                """;

        mockMvc.perform(post("/api/buylist/" + trackingToken + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerMsgJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole", is("CUSTOMER")))
                .andExpect(jsonPath("$.message", is("Just checking if you received my submission!")));
    }

    @Test
    void adminEndpoints_unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/buylist/admin/submissions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/buylist/admin/submissions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFERED\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/buylist/admin/submissions/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoints_authenticated_shouldSucceed() throws Exception {
        // Create a submission first
        String submitJson = """
                {
                    "customerEmail": "trainer2@example.com",
                    "customerName": "Blue",
                    "cardName": "Blastoise 1st Edition",
                    "cardSet": "Base Set",
                    "askingPrice": 1200.00,
                    "additionalComments": "Graded 9.5"
                }
                """;

        MvcResult submitResult = mockMvc.perform(post("/api/buylist/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson))
                .andExpect(status().isCreated())
                .andReturn();

        long submissionId = objectMapper.readTree(submitResult.getResponse().getContentAsString()).get("id").asLong();

        // 1. Admin list submissions
        mockMvc.perform(get("/api/buylist/admin/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())));

        // 2. Admin update status
        mockMvc.perform(patch("/api/buylist/admin/submissions/" + submissionId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UNDER_REVIEW")));

        // 3. Admin send message
        String adminMsgJson = """
                {
                    "message": "We reviewed your card and can proceed with inspection."
                }
                """;

        mockMvc.perform(post("/api/buylist/admin/submissions/" + submissionId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminMsgJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderRole", is("ADMIN")))
                .andExpect(jsonPath("$.message", is("We reviewed your card and can proceed with inspection.")));
    }
}
