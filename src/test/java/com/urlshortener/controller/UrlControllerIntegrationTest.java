package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.ShortenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests against H2 in-memory database.
 *
 * <p>Each test runs in a transaction that is rolled back afterwards,
 * so tests are fully isolated from one another.
 *
 * <p>Covers:
 * <ul>
 *   <li>Shorten → redirect round-trip</li>
 *   <li>Unknown code returns 404</li>
 *   <li>Duplicate URL returns existing mapping</li>
 *   <li>Custom alias happy path</li>
 *   <li>Custom alias conflict (409)</li>
 *   <li>Invalid URL (400)</li>
 *   <li>Blank URL (400)</li>
 *   <li>Stats endpoint</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UrlControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── POST /shorten ─────────────────────────────────────────────────────────

    @Test
    void shortenAndRedirect_roundTrip() throws Exception {
        // 1. Shorten
        String body = objectMapper.writeValueAsString(
                new ShortenRequest("https://www.example.com/integration-test", null));

        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").exists())
                .andExpect(jsonPath("$.existing").value(false))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String shortCode = objectMapper.readTree(responseJson).get("shortCode").asText();
        assertThat(shortCode).hasSize(7).matches("[A-Za-z0-9]+");

        // 2. Redirect
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://www.example.com/integration-test"));
    }

    @Test
    void unknownCode_returns404() throws Exception {
        mockMvc.perform(get("/nonExistentCode123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Short Code Not Found"));
    }

    @Test
    void duplicateUrl_returnsExistingMapping() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ShortenRequest("https://www.duplicate-test.com", null));

        // First request
        MvcResult first = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        String firstCode = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("shortCode").asText();

        // Second request — same URL
        MvcResult second = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existing").value(true))
                .andReturn();
        String secondCode = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("shortCode").asText();

        assertThat(firstCode).isEqualTo(secondCode);
    }

    @Test
    void customAlias_createsNamedShortLink() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ShortenRequest("https://www.example.com", "my-test-alias"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("my-test-alias"));

        // Verify redirect works
        mockMvc.perform(get("/my-test-alias"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://www.example.com"));
    }

    @Test
    void customAlias_conflict_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ShortenRequest("https://www.example.com", "conflict-alias"));

        // First time — should succeed
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // Second time — same alias, different or same URL → 409
        String body2 = objectMapper.writeValueAsString(
                new ShortenRequest("https://www.another.com", "conflict-alias"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Alias Already Taken"));
    }

    @Test
    void invalidUrl_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ShortenRequest("not-a-url", null));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid URL"));
    }

    @Test
    void blankUrl_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ShortenRequest("", null));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ftpUrl_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                new ShortenRequest("ftp://files.example.com/data", null));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("scheme")));
    }

    @Test
    void stats_returnsHitCount() throws Exception {
        // Shorten a URL
        String body = objectMapper.writeValueAsString(
                new ShortenRequest("https://www.stats-test.com", null));
        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        String code = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shortCode").asText();

        // Trigger a redirect to increment hit count
        mockMvc.perform(get("/" + code)).andExpect(status().isMovedPermanently());

        // Check stats
        mockMvc.perform(get("/api/stats/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hitCount").value(1))
                .andExpect(jsonPath("$.shortCode").value(code));
    }

    @Test
    void stats_unknownCode_returns404() throws Exception {
        mockMvc.perform(get("/api/stats/doesNotExist"))
                .andExpect(status().isNotFound());
    }
}
