package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class ActivityAIService {
    private final GeminiService geminiService;
    private static final String SERVICE_NAME = "[ActivityAIService]";

    public void generateRecommendation(Activity activity) {
        log.info("{} Starting recommendation generation for activityId={}, userId={}, type={}",
                SERVICE_NAME, activity.getId(), activity.getUserId(), activity.getType());


        try {
            // Step 1: Validate activity data
            log.debug("{} Validating activity data: type={}, duration={}, calories={}",
                    SERVICE_NAME, activity.getType(), activity.getDuration(), activity.getCaloriesBurned());

            if (activity.getType() == null || activity.getDuration() == null) {
                log.error("{} Invalid activity data: type or duration is null", SERVICE_NAME);
                throw new IllegalArgumentException("Activity type and duration are required");
            }

            // Step 2: Create prompt for activity
            log.info("{} Creating prompt for activity type: {}", SERVICE_NAME, activity.getType());
            String prompt = createPromptForActivity(activity);
            log.info("{} Prompt created successfully, length={} characters", SERVICE_NAME, prompt.length());
            log.debug("{} Prompt preview: {}", SERVICE_NAME,
                    prompt.length() > 300 ? prompt.substring(0, 300) + "..." : prompt);

            // Step 3: Request recommendations from Gemini
            log.info("{} Requesting AI recommendations from Gemini service for activityId={}",
                    SERVICE_NAME, activity.getId());

            long startTime = System.currentTimeMillis();
            String aiResponse = geminiService.getRecommendations(prompt);
            long duration = System.currentTimeMillis() - startTime;

            if (aiResponse == null) {
                log.warn("{} Received null AI response for activityId={}", SERVICE_NAME, activity.getId());
                return;
            }

            log.info("{} ✓ Received AI response for activityId={}, responseLength={}, duration={}ms",
                    SERVICE_NAME, activity.getId(), aiResponse.length(), duration);
            log.debug("{} Response preview: {}", SERVICE_NAME,
                    aiResponse.length() > 300 ? aiResponse.substring(0, 300) + "..." : aiResponse);

            // Step 4: Parse and process response
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(aiResponse);
                log.debug("{} Successfully parsed AI response JSON structure", SERVICE_NAME);

                // Validate response structure
                if (root.has("candidates") && root.get("candidates").isArray()) {
                    log.debug("{} Valid Gemini response structure detected", SERVICE_NAME);
                } else {
                    log.warn("{} Response structure does not match expected Gemini format", SERVICE_NAME);
                }
            } catch (Exception e) {
                log.warn("{} Could not parse AI response as JSON: {}", SERVICE_NAME, e.getMessage());
            }

            log.info("{} ✓ Successfully processed recommendation for activityId={}, userId={}",
                    SERVICE_NAME, activity.getId(), activity.getUserId());

        } catch (IllegalArgumentException e) {
            log.error("{} Validation error for activityId={}: {}", SERVICE_NAME, activity.getId(), e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("{} ✗ Failed to generate recommendation for activityId={}, userId={}, type={}: {}",
                    SERVICE_NAME, activity.getId(), activity.getUserId(), activity.getType(), e.getMessage());
            log.debug("{} Full stack trace for activityId={}:", SERVICE_NAME, activity.getId(), e);
            throw new RuntimeException("Failed to generate AI recommendation for activity: " + activity.getId(), e);
        }
    }

    private String createPromptForActivity(Activity activity) {
        log.debug("{} Creating prompt for activity: type={}, duration={}, calories={}",
                SERVICE_NAME, activity.getType(), activity.getDuration(), activity.getCaloriesBurned());

        String prompt = String.format("""
You are an Elite Sports Physiologist and Senior Exercise Scientist. Analyze the supplied workout data and produce one single raw JSON object — nothing else. Do not include Markdown, explanations, commentary, or any additional keys. Use double quotes for strings and valid JSON syntax only.

(Short coach voice for human-like clarity: be concise, confident, and encouraging — write as an experienced practitioner talking to an athlete.)

STRICT REQUIREMENTS
1. Output MUST be a single valid JSON object and nothing else.
2. Output must match this exact top-level structure and keys:
   - "analysis": object with fields "overall", "pace", "heartRate", "caloriesBurned".
   - "improvements": array of strings (format: "Area — Detailed recommendation").
   - "suggestions": array of strings (format: "WorkoutName — Description").
   - "safety": array of strings.
3. Do NOT add any other fields.
4. Return raw JSON only (no markdown code blocks).
5. Keep advice precise but **simple to understand**. Avoid medical jargon unless necessary.
6. **Adapt technical depth:** For high-intensity/advanced efforts, you can use terms like VO2 or eccentric loading. For standard/beginner efforts, use plain English (e.g., "posture", "breathing", "step rate").
7. Include a brief numeric rationale (e.g., calories/min) inside the analysis fields when inferring intensity.
8. Use the "Key — Value" string format for lists so they map correctly to a Java List<String> model.

HUMAN-LIKE TONE GUIDELINES
- Sound like a smart personal trainer, not a textbook.
- Use 1–2 short humanizing clauses where appropriate (e.g., "Practical tip: ...", "Quick check: ...").

REQUIRED JSON TEMPLATE — USE THIS FORMAT EXACTLY
{
  "analysis": {
    "overall": "Inferred intensity with numeric rationale (e.g. 'Vigorous effort — 12.0 cal/min; you really pushed the pace here').",
    "pace": "Pace/tempo analysis (e.g. 'Consistent pacing, but you started a bit too fast').",
    "heartRate": "Inferred zone (e.g. 'Est. 70-80%% HRmax - mostly aerobic zone') with short rationale.",
    "caloriesBurned": "Metabolic demand interpretation."
  },
  "improvements": [
    "Cadence — Try taking shorter, quicker steps to take the pressure off your knees.",
    "Breathing — Focus on deep belly breaths to lower your heart rate during rest periods.",
    "Posture — Keep your chest up and shoulders relaxed when you get tired."
  ],
  "suggestions": [
    "Interval Run — 4 sets of 4 minutes hard running, 2 minutes easy jogging.",
    "Active Recovery — Go for a light 20-minute walk to help your legs recover."
  ],
  "safety": [
    "Keep your back straight and core tight to avoid lower back strain.",
    "Stop immediately if you feel dizzy or lightheaded during high exertion."
  ]
}

INPUT DATA TO ANALYZE:
Activity Type: %s
Duration: %d minutes
Calories Burned: %d

""", activity.getType(), activity.getDuration(), activity.getCaloriesBurned());

        log.debug("{} Prompt generated successfully, totalLength={} characters", SERVICE_NAME, prompt.length());
        return prompt;
    }
}
