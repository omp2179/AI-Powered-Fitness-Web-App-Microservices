package com.fitness.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@Slf4j
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        // Add detailed logging filter: method, url, headers, response status
        return WebClient.builder()
                .filter((request, next) -> {
                    long startTime = System.currentTimeMillis();
                    String requestId = java.util.UUID.randomUUID().toString();

                    log.info("[WebClient-{}] -> REQUEST: {} {}", requestId, request.method(), request.url());
                    log.debug("[WebClient-{}] Headers: {}", requestId, request.headers());

                    return next.exchange(request)
                            .doOnNext(response -> {
                                long duration = System.currentTimeMillis() - startTime;
                                log.info("[WebClient-{}] <- RESPONSE: status={} ({}ms)", requestId, response.statusCode(), duration);
                                log.debug("[WebClient-{}] Response headers: {}", requestId, response.headers().asHttpHeaders());
                            })
                            .onErrorResume(ex -> {
                                long duration = System.currentTimeMillis() - startTime;
                                log.error("[WebClient-{}] !! ERROR: {} ({}ms)", requestId, ex.getMessage(), duration, ex);
                                log.debug("[WebClient-{}] Error type: {}", requestId, ex.getClass().getName());
                                return Mono.error(ex);
                            });
                });
    }

    @Bean
    public WebClient userServiceWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.baseUrl("http://USER-SERVICE").build();
    }
}