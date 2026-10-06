package com.tailorcards.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.dto.CategoryResponse;
import com.tailorcards.api.dto.ProductPokemontcgCandidateDto;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.dto.SnapshotSyncResponse;
import com.tailorcards.api.dto.UpdatePokemontcgIdRequest;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.mapper.ProductMapper;
import com.tailorcards.api.service.ProductPokemontcgBackfillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProductPokemontcgBackfillService backfillService;

    @MockitoBean
    private ProductMapper productMapper;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin can list unlinked products lacking pokemontcg_id")
    void getUnlinkedProducts_adminAllowed() throws Exception {
        Category cat = Category.builder().id(1L).name("Singles").build();
        Product p = Product.builder().id(10L).name("Pikachu").stock(5).price(new BigDecimal("25.00")).category(cat).build();
        CategoryResponse catDto = new com.tailorcards.api.dto.CategoryResponse(1L, "Singles", "desc");
        ProductResponse dto = new ProductResponse(10L, "Pikachu", "desc", new BigDecimal("25.00"), "img", 5, catDto, "58/102", "Base Set", "NM", "RAW", "AVAILABLE", null);

        when(backfillService.getUnlinkedProducts()).thenReturn(List.of(p));
        when(productMapper.toResponse(any(Product.class))).thenReturn(dto);

        mockMvc.perform(get("/api/admin/products/unlinked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Pikachu"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin can search candidates for a product")
    void getCandidates_adminAllowed() throws Exception {
        ProductPokemontcgCandidateDto candidate = new ProductPokemontcgCandidateDto(
                "base1-58", "Pikachu", "Base Set", "58", "http://img.com/pika.png", new BigDecimal("45.00")
        );
        when(backfillService.findCandidates(eq(10L), any())).thenReturn(List.of(candidate));

        mockMvc.perform(get("/api/admin/products/10/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardId").value("base1-58"))
                .andExpect(jsonPath("$[0].name").value("Pikachu"))
                .andExpect(jsonPath("$[0].marketPriceUsd").value(45.00));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin can confirm and update pokemontcg_id on product")
    void updatePokemontcgId_adminAllowed() throws Exception {
        Category cat = Category.builder().id(1L).name("Singles").build();
        Product updatedProduct = Product.builder()
                .id(10L)
                .name("Pikachu")
                .pokemontcgId("base1-58")
                .category(cat)
                .price(new BigDecimal("25.00"))
                .stock(5)
                .build();
        CategoryResponse catDto = new com.tailorcards.api.dto.CategoryResponse(1L, "Singles", "desc");
        ProductResponse dto = new ProductResponse(10L, "Pikachu", "desc", new BigDecimal("25.00"), "img", 5, catDto, "58/102", "Base Set", "NM", "RAW", "AVAILABLE", "base1-58");

        when(backfillService.updateProductPokemontcgId(10L, "base1-58")).thenReturn(updatedProduct);
        when(productMapper.toResponse(updatedProduct)).thenReturn(dto);

        UpdatePokemontcgIdRequest req = new UpdatePokemontcgIdRequest("base1-58");

        mockMvc.perform(put("/api/admin/products/10/pokemontcg-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.pokemontcgId").value("base1-58"));

        verify(backfillService).updateProductPokemontcgId(10L, "base1-58");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Admin can trigger snapshot sync job once")
    void triggerSnapshotSync_adminAllowed() throws Exception {
        SnapshotSyncResponse resp = new SnapshotSyncResponse("SUCCESS", 8, "Synced 8 distinct cards.");
        when(backfillService.triggerSnapshotSync()).thenReturn(resp);

        mockMvc.perform(post("/api/admin/pricing/sync-snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.cardsSynced").value(8));

        verify(backfillService).triggerSnapshotSync();
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role is forbidden from updating pokemontcg_id or triggering snapshots")
    void demoRole_forbiddenFromMutatingAndSyncing() throws Exception {
        UpdatePokemontcgIdRequest req = new UpdatePokemontcgIdRequest("base1-58");

        mockMvc.perform(put("/api/admin/products/10/pokemontcg-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/pricing/sync-snapshots"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/products/unlinked"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous users cannot access admin sync endpoints")
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/products/unlinked"))
                .andExpect(status().isUnauthorized());
    }
}
