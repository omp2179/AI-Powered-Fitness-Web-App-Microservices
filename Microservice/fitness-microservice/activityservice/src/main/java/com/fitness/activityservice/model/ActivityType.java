package com.fitness.activityservice.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public enum ActivityType {
    // Cardio & Walking
    RUNNING,
    WALKING,
    JOGGING,
    HIKING,
    NORDIC_WALKING,
    RUNNING_TREADMILL,
    WALKING_TREADMILL,
    ELLIPTICAL,
    STAIR_CLIMBING,
    JUMPING_ROPE,
    SKIPPING,

    // Cycling
    CYCLING,
    MOUNTAIN_BIKING,
    ROAD_BIKING,
    STATIONARY_BIKING,
    E_BIKING,

    // Swimming & Water Sports
    SWIMMING,
    SWIMMING_POOL,
    SWIMMING_OPEN_WATER,
    KAYAKING,
    STAND_UP_PADDLEBOARDING,
    ROWING,
    ROWING_MACHINE,

    // Strength Training - Broad Categories
    STRENGTH_TRAINING,
    WEIGHTLIFTING,
    CALISTHENICS,

    // Strength Training - Compound Lifts (top gym staples)
    BENCH_PRESS,
    SQUAT,
    DEADLIFT,
    OVERHEAD_PRESS,
    PULL_UP,
    BARBELL_ROW,
    DIPS,
    CLEAN_AND_JERK,
    SNATCH,

    // Strength - Chest & Push
    INCLINE_BENCH_PRESS,
    DECLINE_BENCH_PRESS,
    DUMBBELL_BENCH_PRESS,
    PUSH_UPS,
    CHEST_FLY,
    PEC_DECK,
    CABLE_CROSSOVER,

    // Strength - Back & Pull
    LAT_PULLDOWN,
    SEATED_ROW,
    DUMBBELL_ROW,
    T_BAR_ROW,
    FACE_PULL,
    SHRUGS,

    // Strength - Legs & Lower Body
    LEG_PRESS,
    LUNGE,
    BULGARIAN_SPLIT_SQUAT,
    LEG_EXTENSION,
    LEG_CURL,
    HIP_THRUST,
    ROMANIAN_DEADLIFT,
    GOOD_MORNING,
    CALF_RAISES,

    // Strength - Shoulders
    LATERAL_RAISE,
    FRONT_RAISE,
    REAR_DELT_FLY,
    UPRIGHT_ROW,
    ARNOLD_PRESS,

    // Arms - Biceps & Triceps
    BICEPS_CURL,
    HAMMER_CURL,
    CONCENTRATION_CURL,
    TRICEP_PUSHDOWN,
    CLOSE_GRIP_BENCH_PRESS,
    OVERHEAD_TRICEP_EXTENSION,
    SKULL_CRUSHER,

    // Core & Abs
    PLANK,
    RUSSIAN_TWIST,
    CRUNCH,
    LEG_RAISE,
    BICYCLE_CRUNCH,
    AB_WHEEL_ROLLOUT,
    HANGING_LEG_RAISE,
    WOOD_CHOPPER,

    // HIIT, Circuit & Functional
    HIIT,
    INTERVAL_TRAINING,
    CIRCUIT_TRAINING,
    KETTLEBELL_TRAINING,
    KETTLEBELL_SWING,
    BURPEE,
    MOUNTAIN_CLIMBERS,
    BATTLE_ROPE,
    BOX_JUMP,

    // Mind-Body & Flexibility
    YOGA,
    PILATES,
    MEDITATION,
    GUIDED_BREATHING,
    BARRE,

    // Dance & Cardio Classes
    DANCE,
    ZUMBA,
    CROSSFIT,

    // Combat Sports
    BOXING,
    KICKBOXING,
    MARTIAL_ARTS,

    // Team & Racket Sports
    BASKETBALL,
    FOOTBALL_SOCCER,
    FOOTBALL_AMERICAN,
    TENNIS,
    BADMINTON,
    SQUASH,
    TABLE_TENNIS,
    VOLLEYBALL,
    CRICKET,
    GOLF,

    // Other Sports & Daily Activities
    SKATEBOARDING,
    ROCK_CLIMBING,
    GARDENING,
    HOUSEWORK,

    @JsonEnumDefaultValue
    OTHER
}
