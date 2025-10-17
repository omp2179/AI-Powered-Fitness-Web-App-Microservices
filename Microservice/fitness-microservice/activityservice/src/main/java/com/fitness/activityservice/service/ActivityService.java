package com.fitness.activityservice.service;

import com.fitness.activityservice.ActivityRepository;
import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final KafkaTemplate<String, Activity> kafkaTemplate;

    @Value("${kafka.topic.activity}")
    private String topicName;

    public ActivityResponse trackActivity(ActivityRequest request) {
        // Minimal, helpful logs for beginners
        log.info("[ActivityService] Incoming activity: userId={}, type={}, duration={}, topic={}",
                request.getUserId(), request.getType(), request.getDuration(), topicName);

        Boolean isValidUser = userValidationService.validateUser(request.getUserId());
        if (!isValidUser) {
            log.warn("[ActivityService] User validation failed for userId={}", request.getUserId());
            throw new RuntimeException("Invalid User: " + request.getUserId());
        }
        log.info("[ActivityService] User validation success for userId={}", request.getUserId());

        Activity activity = Activity.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .duration(request.getDuration())
                .caloriesBurned(request.getCaloriesBurned())
                .startTime(request.getStartTime())
                .additionalData(request.getAdditionalData())
                .build();

        Activity savedActivity = activityRepository.save(activity);
        log.info("[ActivityService] Saved activity with id={}", savedActivity.getId());

        // Add simple Kafka send logging without changing behavior
        try {
            log.info("[ActivityService->Kafka] Sending message: topic={}, key(userId)={}, activityId={}",
                    topicName, savedActivity.getUserId(), savedActivity.getId());
            CompletableFuture<SendResult<String, Activity>> future =
                    kafkaTemplate.send(topicName, savedActivity.getUserId(), savedActivity);
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    if (result != null && result.getRecordMetadata() != null) {
                        log.info("[Kafka->ActivityService] Send OK: topic={}, partition={}, offset={}, ts={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                result.getRecordMetadata().timestamp());
                    } else {
                        log.info("[Kafka->ActivityService] Send OK (no metadata available)");
                    }
                } else {
                    log.error("[Kafka->ActivityService] Send FAILED: topic={}, key(userId)={}, error={}",
                            topicName, savedActivity.getUserId(), ex.getMessage(), ex);
                    log.error("[Hint] Check: 1) Kafka running at spring.kafka.bootstrap-servers 2) topic name '{}' exists 3) serializers configured.", topicName);
                }
            });
        } catch (Exception e) {
            log.error("[Kafka->ActivityService] Send threw exception: {}", e.getMessage(), e);
        }

        return mapToResponse(savedActivity);
    }

    private ActivityResponse mapToResponse(Activity activity) {
        ActivityResponse response = new ActivityResponse();
        response.setId(activity.getId());
        response.setUserId(activity.getUserId());
        response.setType(activity.getType());
        response.setDuration(activity.getDuration());
        response.setCaloriesBurned(activity.getCaloriesBurned());
        response.setStartTime(activity.getStartTime());
        response.setAdditionalData(activity.getAdditionalData());
        response.setCreatedAt(activity.getCreatedAt());
        response.setUpdatedAt(activity.getUpdatedAt());
        return response;
    }
}
