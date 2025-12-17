package com.fitness.activityservice.dto;

import com.fitness.activityservice.model.ActivityType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ActivityRequest {
    @NotNull(message = "userId is required")
    private String userId;

    @NotNull(message = "type is required")
    private ActivityType type;

    @NotNull(message = "duration is required")
    @Min(value = 1, message = "duration must be >= 1")
    private Integer duration;

    @NotNull(message = "caloriesBurned is required")
    @Min(value = 0, message = "caloriesBurned must be >= 0")
    private Integer caloriesBurned;

    @NotNull(message = "startTime is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    private Map<String,Object> additionalData;
}
