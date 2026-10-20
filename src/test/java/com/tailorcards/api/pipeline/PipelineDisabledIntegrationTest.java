package com.tailorcards.api.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Price pipeline API without a shared secret")
class PipelineDisabledIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("The endpoints don't exist until PIPELINE_SHARED_SECRET is set")
    void notFound() throws Exception {
        mvc.perform(get("/api/internal/pricing/watchlist")).andExpect(status().isNotFound());
        mvc.perform(post("/api/internal/pricing/snapshots").content("{}")).andExpect(status().isNotFound());
    }
}
