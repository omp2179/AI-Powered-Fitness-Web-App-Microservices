package com.fitness.activityservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserValidationService {
    private final WebClient userServiceWebClient;

    public Boolean validateUser(String userId) {
        log.info("[UserValidation] GET /api/users/{}/validate via Eureka (service=USER-SERVICE)", userId);
        try {
            Boolean result = userServiceWebClient.get()
                    .uri("/api/users/{userId}/validate", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            log.info("[UserValidation] Response: {}", result);
            return result;
        } catch (WebClientResponseException e) {
            log.error("[UserValidation] HTTP {} from USER-SERVICE for userId={}, body={}", e.getRawStatusCode(), userId, e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[UserValidation] Error calling USER-SERVICE for userId={}: {}", userId, e.getMessage(), e);
        }
        return false;
    }
}
