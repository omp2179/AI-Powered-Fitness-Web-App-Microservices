package com.fitness.aiservice.service;

//import com.fitness.activityservice.model.Activity;
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

    @KafkaListener(topics= "${kafka.topic.activity}" , groupId = "activity-processor-group")
    public void processActivity(
            @Payload com.fitness.aiservice.model.Activity activity,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long ts
    ){
        try {
            log.info("[AIService<-Kafka] Message received: topic={}, partition={}, offset={}, key={}, ts={}",
                    topic, partition, offset, key, ts);
            log.info("[AIService] Activity payload: id={}, userId={}, type={}, duration={}, calories={}",
                    activity.getId(), activity.getUserId(), activity.getType(), activity.getDuration(), activity.getCaloriesBurned());
            // ...existing business logic (if any) ... currently just logs
        } catch (Exception e) {
            log.error("[AIService] Error processing message at topic={}, partition={}, offset={}, key={}: {}",
                    topic, partition, offset, key, e.getMessage(), e);
            // rethrow to allow Kafka error handling/retry mechanisms if configured
            throw e;
        }
    }
}
