package com.fitness.aiservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;

import java.util.Map;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Service
@Slf4j
public class GeminiService {
    private final WebClient webClient;

    @Value("${gemini.api.url:}")
    private String geminiApiUrl;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.retry.max-attempts:3}")
    private int maxRetries;

    @Value("${gemini.api.retry.backoff-delay-ms:1000}")
    private long backoffDelay;

    private boolean isConfigured = false;
    private static final String SERVICE_NAME = "[GeminiService]";

    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiService(WebClient.Builder webClientBuilder) {
        // Configure HttpClient with connection and response timeouts
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30))
                // connectTimeout(...) is not available on some Reactor Netty versions; use ChannelOption instead
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TimeUnit.SECONDS.toMillis(10));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        log.debug("{} WebClient initialized with responseTimeout=30s, connectTimeout=10s", SERVICE_NAME);
    }

    @PostConstruct
    public void validateConfiguration() {
        isConfigured = geminiApiUrl != null && !geminiApiUrl.isBlank() &&
                geminiApiKey != null && !geminiApiKey.isBlank();

        log.info("{} Configuration validation - URL configured: {}, Key configured: {}, Ready: {}",
                SERVICE_NAME,
                geminiApiUrl != null && !geminiApiUrl.isBlank(),
                geminiApiKey != null && !geminiApiKey.isBlank(),
                isConfigured);

        if (!isConfigured) {
            log.warn("{} ⚠ Gemini API credentials NOT configured. Please set GEMINI_API_URL and GEMINI_API_KEY environment variables.", SERVICE_NAME);
        } else {
            log.info("{} ✓ Gemini API configuration loaded successfully", SERVICE_NAME);
        }
    }

    public String getRecommendations(String details) {
        log.info("{} Requesting recommendations from Gemini API", SERVICE_NAME);
        String safeDetails = details == null ? "" : details;

        if (details == null) {
            log.warn("{} Request details are null, using empty string", SERVICE_NAME);
        } else {
            log.debug("{} Details length={}, content preview: {}", SERVICE_NAME, safeDetails.length(),
                    safeDetails.length() > 200 ? safeDetails.substring(0, 200) + "..." : safeDetails);
        }

        // If Gemini API is not configured, return mock response for development/testing
        if (!isConfigured) {
            log.warn("{} API not configured - returning mock recommendation (development mode)", SERVICE_NAME);
            return getMockResponse();
        }

        // Implement retry logic with exponential backoff
        return callWithRetry(safeDetails);
    }

    private String callWithRetry(String details) {
        int attempt = 0;
        long currentBackoff = backoffDelay;

        while (attempt < maxRetries) {
            try {
                attempt++;
                log.debug("{} Attempt {}/{} to call Gemini API", SERVICE_NAME, attempt, maxRetries);

                Map<String, Object> requestBody = Map.of(
                        "contents", new Object[]{
                                Map.of("parts", new Object[]{
                                        Map.of("text", details)
                                })
                        }
                );

                log.debug("{} Sending POST request to Gemini API endpoint", SERVICE_NAME);
                long startTime = System.currentTimeMillis();

                String response = webClient.post()
                        .uri(geminiApiUrl)
                        .header("Content-Type", "application/json")
                        .header("X-goog-api-key", geminiApiKey)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                long duration = System.currentTimeMillis() - startTime;
                log.info("{} ✓ Successfully received response from Gemini API (duration={}ms, responseLength={})",
                        SERVICE_NAME, duration, response != null ? response.length() : 0);

                if (response == null) {
                    log.warn("{} Received null response from Gemini API, using mock response", SERVICE_NAME);
                    return getMockResponse();
                }

                // Print to console
                System.out.println("\n====================================");
                System.out.println("[GeminiService] Raw API Response:");
                System.out.println("====================================");
                System.out.println(response);
                System.out.println("====================================\n");

                log.debug("{} Response preview: {}", SERVICE_NAME,
                        response.length() > 300 ? response.substring(0, 300) + "..." : response);

                return response;

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable e) {
                log.warn("{} Attempt {}/{}: HTTP 503 Service Unavailable - {}", SERVICE_NAME, attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    log.info("{} Retrying after {}ms (exponential backoff)", SERVICE_NAME, currentBackoff);
                    if (sleepBackoff(currentBackoff)) {
                        currentBackoff = currentBackoff * 2; // Exponential backoff
                    } else {
                        break;
                    }
                } else {
                    log.error("{} Max retries ({}) exhausted for 503 error", SERVICE_NAME, maxRetries);
                    return getMockResponse();
                }

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden e) {
                log.error("{} HTTP 403 Forbidden - API Key is invalid, expired, or lacks permissions", SERVICE_NAME);
                log.error("{} Error details: {}", SERVICE_NAME, e.getMessage());
                return getMockResponse();

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized e) {
                log.error("{} HTTP 401 Unauthorized - Authentication failed", SERVICE_NAME);
                return getMockResponse();

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest e) {
                log.error("{} HTTP 400 Bad Request - Invalid request format", SERVICE_NAME);
                log.debug("{} Request body: {}", SERVICE_NAME, details.substring(0, Math.min(200, details.length())));
                return getMockResponse();

            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                log.error("{} HTTP {} {} from Gemini API: {}", SERVICE_NAME, e.getStatusCode(),
                        e.getStatusText(), e.getMessage());

                // Retry on 5xx errors (except already handled)
                if (e.getStatusCode().is5xxServerError() && attempt < maxRetries) {
                    log.info("{} Retrying after {}ms for server error", SERVICE_NAME, currentBackoff);
                    if (sleepBackoff(currentBackoff)) {
                        currentBackoff = currentBackoff * 2;
                    } else {
                        break;
                    }
                } else {
                    return getMockResponse();
                }

            } catch (WebClientRequestException e) {
                // Handles low-level I/O errors such as connection refused, DNS issues, timeouts, etc.
                Throwable cause = e.getCause();
                if (cause != null) {
                    log.error("{} WebClient network error: {} - {}", SERVICE_NAME, cause.getClass().getSimpleName(), cause.getMessage());
                } else {
                    log.error("{} WebClient request error: {}", SERVICE_NAME, e.getMessage());
                }

                if (attempt < maxRetries) {
                    log.info("{} Retrying after {}ms for network error", SERVICE_NAME, currentBackoff);
                    if (sleepBackoff(currentBackoff)) {
                        currentBackoff = currentBackoff * 2;
                    } else {
                        break;
                    }
                } else {
                    return getMockResponse();
                }

            } catch (Exception e) {
                log.error("{} Unexpected error while calling Gemini API (attempt {}/{}): {} - {}",
                        SERVICE_NAME, attempt, maxRetries, e.getClass().getSimpleName(), e.getMessage());
                log.debug("{} Full stack trace:", SERVICE_NAME, e);

                if (attempt < maxRetries) {
                    log.info("{} Retrying after {}ms", SERVICE_NAME, currentBackoff);
                    if (sleepBackoff(currentBackoff)) {
                        currentBackoff = currentBackoff * 2;
                    } else {
                        break;
                    }
                } else {
                    return getMockResponse();
                }
            }
        }

        log.warn("{} All retry attempts failed, returning mock response as fallback", SERVICE_NAME);
        return getMockResponse();
    }

    // Helper to centralize sleep and handle interruption
    private boolean sleepBackoff(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            log.error("{} Thread interrupted during backoff", SERVICE_NAME);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String getMockResponse() {
        log.debug("{} Generating mock response for development/testing", SERVICE_NAME);
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode candidates = root.putArray("candidates");
            ObjectNode cand = candidates.addObject();
            ObjectNode content = cand.putObject("content");
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();

            ObjectNode analysis = mapper.createObjectNode();
            analysis.put("overall", "Good workout, well done! Keep this up for better results.");
            analysis.put("pace", "Your pace is consistent and sustainable.");
            analysis.put("heartRate", "Heart rate is in the optimal zone.");
            analysis.put("caloriesBurned", "You burned a good amount of calories today.");

            ObjectNode inner = mapper.createObjectNode();
            inner.set("analysis", analysis);

            ArrayNode improvements = mapper.createArrayNode();
            improvements.add("Intensity — Increase intensity by 10% for better cardio benefits");
            inner.set("improvements", improvements);

            ArrayNode suggestions = mapper.createArrayNode();
            suggestions.add("HIIT — Try high-intensity interval training 2x per week");
            inner.set("suggestions", suggestions);

            ArrayNode safety = mapper.createArrayNode();
            safety.add("Always warm up before exercise");
            safety.add("Stay hydrated");
            safety.add("Cool down after workout");
            inner.set("safety", safety);

            // put inner JSON as a string into the text field to mimic Gemini response structure
            part.put("text", mapper.writeValueAsString(inner));

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("{} Failed to build mock response: {}", SERVICE_NAME, e.getMessage(), e);
            // fallback to a simple minimal response
            return "{\"candidates\":[]}";
        }
    }
}

