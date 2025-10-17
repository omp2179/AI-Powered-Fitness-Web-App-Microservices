package com.fitness.activityservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@Slf4j
public class ActivityserviceApplication {

    @Value("${spring.application.name}")
    private String appName;
    @Value("${server.port}")
    private String serverPort;
    @Value("${eureka.client.serviceUrl.defaultZone}")
    private String eurekaUri;
    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaServers;
    @Value("${kafka.topic.activity}")
    private String activityTopic;

	public static void main(String[] args) {
		SpringApplication.run(ActivityserviceApplication.class, args);
	}

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupConfig() {
        log.info("[Startup] {} is UP on port {}", appName, serverPort);
        log.info("[Startup] Eureka URI: {}", eurekaUri);
        log.info("[Startup] Kafka bootstrap.servers: {}", kafkaServers);
        log.info("[Startup] Kafka topic (activity): {}", activityTopic);
    }
}
