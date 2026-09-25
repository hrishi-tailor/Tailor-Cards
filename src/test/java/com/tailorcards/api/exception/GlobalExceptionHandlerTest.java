package com.tailorcards.api.exception;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Import(GlobalExceptionHandlerTest.TestConflictController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    @RestController
    @RequestMapping("/api/test-conflict")
    static class TestConflictController {

        @GetMapping("/stock-conflict")
        public void throwStockConflict() {
            throw new StockConflictException("item no longer available at requested quantity");
        }

        @GetMapping("/optimistic-lock")
        public void throwOptimisticLock() {
            throw new OptimisticLockException("Stale data detected");
        }

        @GetMapping("/spring-optimistic-lock")
        public void throwSpringOptimisticLock() {
            throw new ObjectOptimisticLockingFailureException("Product", 1L);
        }
    }

    @Test
    void directHandler_stockConflictException_returns409() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/checkout/pay");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockConflict(
                new StockConflictException("item no longer available at requested quantity"),
                request
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals("item no longer available at requested quantity", response.getBody().message());
        assertEquals("/api/checkout/pay", response.getBody().path());
    }

    @Test
    void directHandler_optimisticLockException_returns409CleanMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/checkout/pay");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockConflict(
                new OptimisticLockException("Stale row version"),
                request
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("item no longer available at requested quantity", response.getBody().message());
    }

    @Test
    void stockConflictException_returns409WithCleanMessage() throws Exception {
        mockMvc.perform(get("/api/test-conflict/stock-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("item no longer available at requested quantity"))
                .andExpect(jsonPath("$.path").value("/api/test-conflict/stock-conflict"));
    }

    @Test
    void optimisticLockException_returns409WithCleanMessage() throws Exception {
        mockMvc.perform(get("/api/test-conflict/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("item no longer available at requested quantity"))
                .andExpect(jsonPath("$.path").value("/api/test-conflict/optimistic-lock"));
    }

    @Test
    void springOptimisticLockException_returns409WithCleanMessage() throws Exception {
        mockMvc.perform(get("/api/test-conflict/spring-optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("item no longer available at requested quantity"))
                .andExpect(jsonPath("$.path").value("/api/test-conflict/spring-optimistic-lock"));
    }
}
