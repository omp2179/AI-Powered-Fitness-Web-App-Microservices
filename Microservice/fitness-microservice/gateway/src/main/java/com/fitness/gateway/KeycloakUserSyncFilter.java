package com.fitness.gateway;

import com.fitness.user.RegisterRequest;
import com.fitness.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.text.ParseException;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {

    private final UserService userService;
    @Override
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        log.debug("[KeycloakUserSyncFilter] === Processing request: {} {} ===", exchange.getRequest().getMethod(), exchange.getRequest().getPath());

        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        log.debug("[KeycloakUserSyncFilter] X-User-Id header: {}", userId != null ? "present" : "missing");
        log.debug("[KeycloakUserSyncFilter] Authorization header: {}", token != null ? "present (length=" + token.length() + ")" : "missing");

        // Validate token exists
        if(token == null) {
            log.warn("[KeycloakUserSyncFilter] No authorization token provided, skipping user sync");
            return chain.filter(exchange);
        }

        RegisterRequest registerRequest;
        try {
            log.debug("[KeycloakUserSyncFilter] Extracting user details from JWT token...");
            registerRequest = getUserDetails(token);
            log.debug("[KeycloakUserSyncFilter] Successfully extracted user details. Email: {}, KeycloakId: {}", registerRequest.getEmail(), registerRequest.getKeycloakId());
        } catch (Exception e) {
            log.error("[KeycloakUserSyncFilter] Failed to extract user details from token. Error: {}", e.getMessage(), e);
            return chain.filter(exchange);
        }

        // Use keycloakId from token if X-User-Id header is not provided
        if(userId == null) {
            userId = registerRequest.getKeycloakId();
            log.debug("[KeycloakUserSyncFilter] X-User-Id was missing, using keycloakId from token: {}", userId);
        } else {
            log.debug("[KeycloakUserSyncFilter] Using X-User-Id from header: {}", userId);
        }

        final String finalUserId = userId;
        log.info("[KeycloakUserSyncFilter] Starting user validation for userId: {}", finalUserId);

        return userService.validateUser(finalUserId)
                .flatMap(exist ->{
                    if(!exist) {
                        log.info("[KeycloakUserSyncFilter] User not found in database. Registering userId: {}, email: {}", finalUserId, registerRequest.getEmail());
                        return userService.registerUser(registerRequest);
                    }
                    else{
                        log.info("[KeycloakUserSyncFilter] User already exists in database, skipping registration for userId: {}", finalUserId);
                        return Mono.empty();
                    }
                })
                .then(Mono.defer(()-> {
                    log.debug("[KeycloakUserSyncFilter] Adding X-User-Id header to request: {}", finalUserId);
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().header(
                            "X-User-Id", finalUserId
                    ).build();
                    log.debug("[KeycloakUserSyncFilter] Request mutated successfully, proceeding to next filter");
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                }))
                .onErrorResume(e -> {
                    log.error("[KeycloakUserSyncFilter] Error occurred in filter chain for userId: {}, error: {}, proceeding without sync", finalUserId, e.getMessage(), e);
                    log.debug("[KeycloakUserSyncFilter] Error stack trace for debugging:", e);
                    return chain.filter(exchange);
                });
    }

    private RegisterRequest getUserDetails(String token) {
        log.debug("[getUserDetails] Starting JWT token parsing...");
        try {
            String tokenWithoutBearer = token.replace("Bearer ", "").trim();
            log.debug("[getUserDetails] Token format: Bearer+JWT, cleaned token length: {} characters", tokenWithoutBearer.length());

            log.debug("[getUserDetails] Parsing JWT token...");
            SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);
            log.debug("[getUserDetails] JWT parsing successful");

            log.debug("[getUserDetails] Extracting claims from JWT...");
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            String email = claims.getStringClaim("email");
            String keycloakId = claims.getStringClaim("sub");
            String firstName = claims.getStringClaim("given_name");
            String lastName = claims.getStringClaim("family_name");

            log.debug("[getUserDetails] Extracted claims - email: {}, keycloakId: {}, firstName: {}, lastName: {}",
                    email != null ? "present" : "missing",
                    keycloakId != null ? keycloakId : "missing",
                    firstName != null ? "present" : "missing",
                    lastName != null ? "present" : "missing");

            RegisterRequest request = new RegisterRequest();
            request.setEmail(email);
            request.setKeycloakId(keycloakId);
            request.setFirstName(firstName);
            request.setLastName(lastName);
            request.setPassword("dummy123123");

            log.info("[getUserDetails] RegisterRequest created successfully: email={}, keycloakId={}", email, keycloakId);
            return request;

        } catch (ParseException e) {
            log.error("[getUserDetails] JWT parsing failed - Error: {}", e.getMessage(), e);
            log.debug("[getUserDetails] Failed to parse token. Token may be malformed, expired, or invalid");
            throw new RuntimeException("JWT parsing failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[getUserDetails] Unexpected error during token processing - Error: {}", e.getMessage(), e);
            throw new RuntimeException("Unexpected error processing token: " + e.getMessage(), e);
        }
    }
}
