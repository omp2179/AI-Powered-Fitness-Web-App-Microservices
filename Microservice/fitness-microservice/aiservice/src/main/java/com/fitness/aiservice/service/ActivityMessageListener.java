package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.ActivityRepository;
import com.fitness.aiservice.repository.RecommendationRepository;
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
    private final ActivityRepository activityRepository;
    private final RecommendationRepository recommendationRepository;
    private static final String SERVICE_NAME = "[ActivityMessageListener]";

    @KafkaListener(topics = "${kafka.topic.activity}", groupId = "activity-processor-group")
    public void processActivity(
            @Payload Activity activity,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long ts
    ) {
        log.info("{} ▶ Message received: topic={}, partition={}, offset={}, key={}, ts={}",
                SERVICE_NAME, topic, partition, offset, key, ts);

        try {
            // Step 1: Validate incoming activity payload
            if (activity == null) {
                log.error("{} ✗ Received null activity payload at offset={}, partition={}",
                        SERVICE_NAME, offset, partition);
                throw new IllegalArgumentException("Activity payload cannot be null");
            }

            log.info("{} Activity payload: id={}, userId={}, type={}, duration={}, calories={}",
                    SERVICE_NAME, activity.getId(), activity.getUserId(), activity.getType(),
                    activity.getDuration(), activity.getCaloriesBurned());

            // Step 2: Validate required fields
            if (activity.getId() == null) {
                log.error("{} ✗ Activity ID is null at offset={}", SERVICE_NAME, offset);
                throw new IllegalArgumentException("Activity ID cannot be null");
            }

            if (activity.getUserId() == null) {
                log.error("{} ✗ User ID is null for activityId={}, offset={}",
                        SERVICE_NAME, activity.getId(), offset);
                throw new IllegalArgumentException("User ID cannot be null");
            }

            if (activity.getType() == null) {
                log.error("{} ✗ Activity type is null for activityId={}, userId={}, offset={}",
                        SERVICE_NAME, activity.getId(), activity.getUserId(), offset);
                throw new IllegalArgumentException("Activity type cannot be null");
            }

            log.debug("{} Validation passed for activityId={}", SERVICE_NAME, activity.getId());

            // Step 3: Save activity to local MongoDB (for 7-day history queries)
            log.info("{} Saving activity to local MongoDB: activityId={}, userId={}, startTime={}",
                    SERVICE_NAME, activity.getId(), activity.getUserId(), activity.getStartTime());

            // Ensure startTime is set (critical for query to work)
            if (activity.getStartTime() == null) {
                log.warn("{} ⚠ startTime is null for activityId={}, setting to now",
                        SERVICE_NAME, activity.getId());
                activity.setStartTime(java.time.LocalDateTime.now());
            }

            Activity savedActivity = activityRepository.save(activity);
            log.info("{} ✓ Activity saved locally: activityId={}, userId={}, startTime={}",
                    SERVICE_NAME, savedActivity.getId(), savedActivity.getUserId(), savedActivity.getStartTime());

            // Verify activity was actually saved by counting
            long activityCount = activityRepository.count();
            log.info("{} Total activities in local DB: {}", SERVICE_NAME, activityCount);

            // Verify this specific activity can be retrieved
            java.util.Optional<Activity> verifyActivity = activityRepository.findById(savedActivity.getId());
            if (verifyActivity.isPresent()) {
                log.info("{} ✓ Verified activity retrieval: activityId={}, startTime={}",
                        SERVICE_NAME, verifyActivity.get().getId(), verifyActivity.get().getStartTime());
            } else {
                log.error("{} ✗ Failed to retrieve just-saved activity: activityId={}",
                        SERVICE_NAME, savedActivity.getId());
            }

            // Step 4: Generate recommendation with 7-day context
            log.debug("{} Starting recommendation generation for activityId={}", SERVICE_NAME, activity.getId());
            long startTime = System.currentTimeMillis();

            Recommendation recommendation=activityAIService.generateRecommendation(activity);

            // Avoid NPE and make it obvious in logs when recommendation couldn't be generated.
            if (recommendation == null) {
                log.warn("{} ⚠ Recommendation is null; skipping save. activityId={}, userId={}, offset={}, partition={}",
                        SERVICE_NAME, activity.getId(), activity.getUserId(), offset, partition);
                return;
            }

            log.debug("{} Saving recommendation: activityId={}, userId={}, improvements={}, suggestions={}, safety={}",
                    SERVICE_NAME,
                    recommendation.getActivityId(),
                    recommendation.getUserId(),
                    recommendation.getImprovements() == null ? 0 : recommendation.getImprovements().size(),
                    recommendation.getSuggestions() == null ? 0 : recommendation.getSuggestions().size(),
                    recommendation.getSafety() == null ? 0 : recommendation.getSafety().size());

            Recommendation savedRecommendation = recommendationRepository.save(recommendation);
            log.info("{} ✓ Recommendation saved: id={}, activityId={}, userId={}",
                    SERVICE_NAME, savedRecommendation.getId(), savedRecommendation.getActivityId(),
                    savedRecommendation.getUserId());

            // Verify recommendation was saved
            long recommendationCount = recommendationRepository.count();
            log.info("{} Total recommendations in DB: {}", SERVICE_NAME, recommendationCount);

            long duration = System.currentTimeMillis() - startTime;
            log.info("{} ✓ Successfully processed activity message: activityId={}, userId={}, processingTime={}ms, offset={}",
                    SERVICE_NAME, activity.getId(), activity.getUserId(), duration, offset);

        } catch (IllegalArgumentException e) {
            log.error("{} ✗ Validation error - topic={}, partition={}, offset={}, key={}: {}",
                    SERVICE_NAME, topic, partition, offset, key, e.getMessage());
            log.debug("{} Validation error details", SERVICE_NAME, e);
            // Don't rethrow validation errors to prevent infinite Kafka retries

        } catch (RuntimeException e) {
            log.error("{} ✗ Runtime error processing message - topic={}, partition={}, offset={}: {}",
                    SERVICE_NAME, topic, partition, offset, e.getMessage());
            log.debug("{} Runtime error stack trace:", SERVICE_NAME, e);
            // Rethrow runtime exceptions to allow Kafka error handling/retry mechanisms
            throw e;

        } catch (Exception e) {
            log.error("{} ✗ Unexpected error processing message at topic={}, partition={}, offset={}, key={}: {} - {}",
                    SERVICE_NAME, topic, partition, offset, key, e.getClass().getSimpleName(), e.getMessage());
            log.debug("{} Full error stack trace:", SERVICE_NAME, e);
            // Rethrow to allow Kafka error handling
            throw new RuntimeException("Error processing Kafka message: " + e.getMessage(), e);
        }
    }
}
