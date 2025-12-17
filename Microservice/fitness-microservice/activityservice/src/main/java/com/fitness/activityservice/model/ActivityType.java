package com.fitness.activityservice.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum ActivityType {
    RUNNING,
    CYCLING,
    SWIMMING,
    WALKING,
    YOGA,
    STRENGTH_TRAINING,
    HIIT,
    DANCE,
    CARDIO,
    SKIPPING,
    @JsonEnumDefaultValue
    OTHER
}
