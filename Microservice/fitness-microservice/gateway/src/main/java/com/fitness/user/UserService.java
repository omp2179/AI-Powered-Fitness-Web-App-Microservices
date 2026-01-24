package com.fitness.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;


@RequiredArgsConstructor
@Slf4j
@Service
public class UserService {
    private final WebClient userServiceWebClient;

    public Mono<Boolean> validateUser(String userId) {
        log.debug("[UserValidation] === Starting user validation ===");
        log.info("[UserValidation] GET /api/users/{}/validate via Eureka (service=USER-SERVICE)", userId);
        log.debug("[UserValidation] Request initiated for userId: {}", userId);

        try {
            Mono<Boolean> result = userServiceWebClient.get()
                    .uri("/api/users/{userId}/validate", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .doOnNext(exists -> {
                        if (exists) {
                            log.info("[UserValidation] User EXISTS in database - userId: {}", userId);
                        } else {
                            log.info("[UserValidation] User DOES NOT EXIST in database - userId: {}", userId);
                        }
                    })
                    .onErrorResume(WebClientResponseException.class, e -> {
                        log.error("[UserValidation] HTTP Error {} from USER-SERVICE - userId: {}", e.getStatusCode(), userId);
                        log.error("[UserValidation] Response body: {}", e.getResponseBodyAsString());

                        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                            log.info("[UserValidation] User validation returned 404 - User not found - userId: {}", userId);
                            return Mono.just(false);
                        } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                            log.error("[UserValidation] Bad request (400) for user validation - userId: {}", userId);
                            return Mono.error(new RuntimeException("Bad request for user validation: " + userId));
                        } else {
                            log.error("[UserValidation] Unexpected HTTP error {} - userId: {}", e.getStatusCode(), userId);
                            return Mono.error(new RuntimeException("Error validating user: " + userId));
                        }
                    });

            log.debug("[UserValidation] Mono chain created, awaiting response...");
            return result;

        } catch (WebClientResponseException e) {
            log.error("[UserValidation] WebClientResponseException - HTTP {} from USER-SERVICE for userId={}", e.getStatusCode(), userId);
            log.error("[UserValidation] Response body: {}", e.getResponseBodyAsString(), e);
            return Mono.error(e);
        } catch (Exception e) {
            log.error("[UserValidation] Unexpected exception calling USER-SERVICE for userId={}: {}", userId, e.getMessage(), e);
            log.debug("[UserValidation] Exception type: {}, Stack trace:", e.getClass().getName(), e);
            return Mono.error(e);
        }
    }

    public Mono<UserResponse> registerUser(RegisterRequest registerRequest) {
        log.debug("[UserRegistration] === Starting user registration ===");
        log.info("[UserRegistration] POST /api/users/register via Eureka (service=USER-SERVICE)");
        log.debug("[UserRegistration] Registering user - Email: {}, KeycloakId: {}, FirstName: {}, LastName: {}",
                registerRequest.getEmail(),
                registerRequest.getKeycloakId(),
                registerRequest.getFirstName(),
                registerRequest.getLastName());

        try {
            Mono<UserResponse> result = userServiceWebClient.post()
                    .uri("/api/users/register")
                    .bodyValue(registerRequest)
                    .retrieve()
                    .bodyToMono(UserResponse.class)
                    .doOnNext(response -> {
                        log.info("[UserRegistration] User registered successfully - Email: {}, UserId: {}",
                                registerRequest.getEmail(),
                                response != null ? response.getId() : "N/A");
                        log.debug("[UserRegistration] Registration response: {}", response);
                    })
                    .onErrorResume(WebClientResponseException.class, e -> {
                        log.error("[UserRegistration] HTTP Error {} from USER-SERVICE - Email: {}", e.getStatusCode(), registerRequest.getEmail());
                        log.error("[UserRegistration] Response body: {}", e.getResponseBodyAsString());

                        if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                            log.error("[UserRegistration] Bad request (400) - Invalid registration data - Email: {}", registerRequest.getEmail());
                            return Mono.error(new RuntimeException("Bad request for user registration: " + registerRequest.getEmail()));
                        } else {
                            log.error("[UserRegistration] Unexpected HTTP error {} - Email: {}", e.getStatusCode(), registerRequest.getEmail());
                            return Mono.error(new RuntimeException("Error registering user: " + registerRequest.getEmail()));
                        }
                    });

            log.debug("[UserRegistration] Mono chain created, awaiting response...");
            return result;

        } catch (WebClientResponseException e) {
            log.error("[UserRegistration] WebClientResponseException - HTTP {} from USER-SERVICE for email={}", e.getStatusCode(), registerRequest.getEmail());
            log.error("[UserRegistration] Response body: {}", e.getResponseBodyAsString(), e);
            return Mono.error(e);
        } catch (Exception e) {
            log.error("[UserRegistration] Unexpected exception calling USER-SERVICE for email={}: {}", registerRequest.getEmail(), e.getMessage(), e);
            log.debug("[UserRegistration] Exception type: {}, Stack trace:", e.getClass().getName(), e);
            return Mono.error(e);
        }
    }
};