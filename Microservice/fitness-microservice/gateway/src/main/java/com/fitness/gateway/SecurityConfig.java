package com.fitness.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/eureka/**").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public WebFilter authenticationLoggingFilter() {
        return (ServerWebExchange exchange, org.springframework.web.server.WebFilterChain chain) ->
                exchange.getPrincipal()
                        .doOnNext(principal -> {
                            String path = exchange.getRequest().getPath().toString();
                            String user = principal.getName();
                            logger.info("User AUTHENTICATED: {} | Path: {}", user, path);
                        })
                        .switchIfEmpty(Mono.fromRunnable(() -> {
                            String path = exchange.getRequest().getPath().toString();
                            logger.warn("User NOT AUTHENTICATED | Path: {}", path);
                        }))
                        .then(chain.filter(exchange));
    }
}
