package com.fitness.aiservice.service;

//import com.fitness.activityservice.model.Activity;
import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAIService activityAIService;

    @KafkaListener(topics= "${kafka.topic.activity}" , groupId = "activity-processor-group")
    public void processActivity(
            @Payload Activity activity,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long ts


    ){
        log.info("[AIService<-Kafka] ▶ Message received: topic={}, partition={}, offset={}, key={}, ts={}",
                topic, partition, offset, key, ts);

        try {
            // Validate incoming activity
            if (activity == null) {
                log.error("[AIService] ✗ Received null activity payload at offset={}", offset);
                throw new IllegalArgumentException("Activity payload cannot be null");
            }

            log.info("[AIService] Activity payload: id={}, userId={}, type={}, duration={}, calories={}",
                    activity.getId(), activity.getUserId(), activity.getType(),
                    activity.getDuration(), activity.getCaloriesBurned());

            // Validate required fields
            if (activity.getId() == null || activity.getUserId() == null || activity.getType() == null) {
                log.error("[AIService] ✗ Invalid activity: missing required fields - id={}, userId={}, type={}",
                        activity.getId(), activity.getUserId(), activity.getType());
                throw new IllegalArgumentException("Activity must have id, userId, and type");
            }

            log.debug("[AIService] Starting recommendation generation process for activityId={}", activity.getId());
            long startTime = System.currentTimeMillis();

            activityAIService.generateRecommendation(activity);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AIService] ✓ Successfully processed activity message: activityId={}, userId={}, processingTime={}ms, offset={}",
                    activity.getId(), activity.getUserId(), duration, offset);

        } catch (IllegalArgumentException e) {
            log.error("[AIService] ✗ Validation error at topic={}, partition={}, offset={}, key={}: {}",
                    topic, partition, offset, key, e.getMessage());
            // Don't rethrow validation errors to prevent infinite retries

        } catch (Exception e) {
            log.error("[AIService] ✗ Error processing message at topic={}, partition={}, offset={}, key={}: {}",
                    topic, partition, offset, key, e.getMessage(), e);
            // rethrow to allow Kafka error handling/retry mechanisms if configured
            throw e;
        }
    }
}
