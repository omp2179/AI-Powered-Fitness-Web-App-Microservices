package com.fitness.activityservice.config;

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
        // Add lightweight logging filter: method, url, and response status
        return WebClient.builder()
                .filter((request, next) -> {
                    log.info("[WebClient] -> {} {}", request.method(), request.url());
                    return next.exchange(request)
                            .doOnNext(response -> log.info("[WebClient] <- status={}", response.statusCode()))
                            .onErrorResume(ex -> {
                                log.error("[WebClient] !! error={}", ex.getMessage(), ex);
                                return Mono.error(ex);
                            });
                });
    }

    @Bean
    public WebClient userServiceWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.baseUrl("http://USER-SERVICE").build();
    }
}