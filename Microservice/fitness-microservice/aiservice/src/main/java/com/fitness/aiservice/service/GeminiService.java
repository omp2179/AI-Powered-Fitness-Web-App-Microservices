package com.fitness.aiservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GeminiService {
    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String getRecommendations(String details) {
        // Basic validation and logging
        log.info("[GeminiService] Requesting recommendations from Gemini API");
        String safeDetails = details == null ? "" : details;
        if (details == null) {
            log.warn("[GeminiService] Request details are null");
        } else {
            log.debug("[GeminiService] Details length={}, preview={}", safeDetails.length(), safeDetails.length() > 200 ? safeDetails.substring(0, 200) + "..." : safeDetails);
        }

        if (geminiApiUrl == null || geminiApiUrl.isEmpty()) {
            log.error("[GeminiService] Gemini API URL is not configured (gemini.api.url)");
            throw new IllegalStateException("Gemini API URL is not configured");
        }

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[] {
                        Map.of("parts", new Object[] {
                                Map.of("text", safeDetails)
                        })
                }
        );

        try {
            log.debug("[GeminiService] Sending POST to {} (X-goog-api-key present: {})", geminiApiUrl, geminiApiKey != null && !geminiApiKey.isEmpty());

            String response = webClient.post()
                    .uri(geminiApiUrl)
                    .header("Content-Type","application/json")
                    .header("X-goog-api-key", geminiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) {
                log.warn("[GeminiService] Received null response from Gemini API for request preview: {}", details != null && details.length() > 100 ? details.substring(0,100) + "..." : details);
            } else {
                log.info("[GeminiService] Received response from Gemini API, length={}", response.length());
                log.debug("[GeminiService] Response preview: {}", response.length() > 500 ? response.substring(0,500) + "..." : response);
            }

            return response;
        } catch (Exception e) {
            // Log full context, do not log secret keys
            log.error("[GeminiService] Error while calling Gemini API (url={}): {}", geminiApiUrl, e.getMessage(), e);
            // Wrap and rethrow to preserve original calling semantics and allow upstream handling
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }
}