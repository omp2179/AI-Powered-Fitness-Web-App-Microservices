package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.AllArgsConstructor;
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

    public Recommendation generateRecommendation(Activity activity) {
        log.info("[ActivityAIService] Starting recommendation generation for activityId={}, userId={}, type={}",
                activity.getId(), activity.getUserId(), activity.getType());

        try {
            log.debug("[ActivityAIService] Creating prompt for activity type: {}", activity.getType());
            String prompt = createPromptForActivity(activity);
            log.debug("[ActivityAIService] Prompt created successfully, length={} characters", prompt.length());

            log.info("[ActivityAIService] Requesting AI recommendations from Gemini service for activityId={}", activity.getId());
            String aiResponse = geminiService.getRecommendations(prompt);
            log.info("[ActivityAIService] Received AI response for activityId={}, responseLength={}",
                    activity.getId(), aiResponse != null ? aiResponse.length() : 0);
            log.debug("[ActivityAIService] AI Response content: {}", aiResponse);

            log.debug("[ActivityAIService] Processing AI response for activityId={}", activity.getId());
            Recommendation recommendation = processAIResponse(activity, aiResponse);

            log.info("[ActivityAIService] ✓ Successfully generated recommendation for activityId={}, userId={}, recommendationId={}",
                    activity.getId(), activity.getUserId(), recommendation.getId());

            return recommendation;
        } catch (Exception e) {
            log.error("[ActivityAIService] ✗ Failed to generate recommendation for activityId={}, userId={}, type={}: {}",
                    activity.getId(), activity.getUserId(), activity.getType(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate AI recommendation for activity: " + activity.getId(), e);
        }
    }

    private Recommendation processAIResponse(Activity activity, String aiResponse) {
        log.debug("[ActivityAIService] Processing AI response for activityId={}", activity.getId());

        try {
            log.debug("[ActivityAIService] Parsing JSON response for activityId={}", activity.getId());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(aiResponse);

            log.debug("[ActivityAIService] Extracting text content from response structure for activityId={}", activity.getId());
            JsonNode textNode = rootNode.path("candidates")
                    .get(0)
                    .path("content")
                    .get("parts")
                    .get(0)
                    .path("text");

            log.debug("[ActivityAIService] Cleaning JSON content (removing markdown code blocks) for activityId={}", activity.getId());
            String jsonContent = textNode.asText()
                    .replaceAll("```json\\n","")
                    .replaceAll("\\n```","")
                    .trim();

            log.debug("[ActivityAIService] Cleaned JSON content for activityId={}: {}", activity.getId(), jsonContent);

            log.debug("[ActivityAIService] Parsing cleaned JSON analysis for activityId={}", activity.getId());
            JsonNode analysisJson = mapper.readTree(jsonContent);
            JsonNode analysisNode = analysisJson.path("analysis");

            log.debug("[ActivityAIService] Building full analysis text for activityId={}", activity.getId());
            StringBuilder fullAnalysis = new StringBuilder();
            addAnalysisSection(fullAnalysis, analysisNode, "overall", "Overall:");
            addAnalysisSection(fullAnalysis, analysisNode, "pace", "Pace:");
            addAnalysisSection(fullAnalysis, analysisNode, "heartRate", "Heart Rate:");
            addAnalysisSection(fullAnalysis, analysisNode, "caloriesBurned", "Calories:");
            log.debug("[ActivityAIService] Analysis text built, length={} characters", fullAnalysis.length());

            log.debug("[ActivityAIService] Extracting improvements, suggestions, and safety guidelines for activityId={}", activity.getId());
            List<String> improvements = extractImprovements(analysisJson.path("improvements"));
            List<String> suggestions = extractSuggestions(analysisJson.path("suggestions"));
            List<String> safety = extractSafetyGuidelines(analysisJson.path("safety"));

            log.debug("[ActivityAIService] Extracted {} improvements, {} suggestions, {} safety guidelines for activityId={}",
                    improvements.size(), suggestions.size(), safety.size(), activity.getId());

            log.debug("[ActivityAIService] Building Recommendation object for activityId={}", activity.getId());
            Recommendation recommendation = Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .id(activity.getType().toString())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safety)
                    .createdAt(LocalDateTime.now())
                    .build();

            log.info("[ActivityAIService] ✓ Successfully processed AI response for activityId={}", activity.getId());
            return recommendation;

        } catch (Exception e) {
            log.error("[ActivityAIService] ✗ Error processing AI response for activityId={}: {}",
                    activity.getId(), e.getMessage(), e);
            log.warn("[ActivityAIService] Falling back to default recommendation for activityId={}", activity.getId());
            return createDefaultRecommendation(activity);
        }
    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        log.warn("[ActivityAIService] Creating default recommendation for activityId={}, userId={}, type={}",
                activity.getId(), activity.getUserId(), activity.getType());

        Recommendation defaultRecommendation = Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .id(activity.getType().toString())
                .recommendation("Unable to generate detailed analysis")
                .improvements(Collections.singletonList("Continue with your current routine"))
                .suggestions(Collections.singletonList("Consider consulting a fitness consultant"))
                .safety(Arrays.asList(
                        "Always warm up before exercise",
                        "Stay hydrated",
                        "Listen to your body"
                ))
                .createdAt(LocalDateTime.now())
                .build();

        log.info("[ActivityAIService] ✓ Default recommendation created for activityId={}", activity.getId());
        return defaultRecommendation;
    }

    private List<String> extractSafetyGuidelines(JsonNode safetyNode) {
        log.debug("[ActivityAIService] Extracting safety guidelines from JSON node");
        List<String> safety = new ArrayList<>();

        if (safetyNode.isArray()) {
            safetyNode.forEach(item -> safety.add(item.asText()));
            log.debug("[ActivityAIService] Extracted {} safety guidelines", safety.size());
        } else {
            log.debug("[ActivityAIService] Safety node is not an array or is missing");
        }

        if (safety.isEmpty()) {
            log.debug("[ActivityAIService] No safety guidelines found, using default");
        }

        return safety.isEmpty() ?
                Collections.singletonList("Follow general safety guidelines") :
                safety;
    }

    private List<String> extractSuggestions(JsonNode suggestionsNode) {
        log.debug("[ActivityAIService] Extracting suggestions from JSON node");
        List<String> suggestions = new ArrayList<>();

        if (suggestionsNode.isArray()) {
            suggestionsNode.forEach(suggestion -> {
                String workout = suggestion.path("workout").asText();
                String description = suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s", workout, description));
            });
            log.debug("[ActivityAIService] Extracted {} suggestions", suggestions.size());
        } else {
            log.debug("[ActivityAIService] Suggestions node is not an array or is missing");
        }

        if (suggestions.isEmpty()) {
            log.debug("[ActivityAIService] No suggestions found, using default");
        }

        return suggestions.isEmpty() ?
                Collections.singletonList("No specific suggestions provided") :
                suggestions;
    }

    private List<String> extractImprovements(JsonNode improvementsNode) {
        log.debug("[ActivityAIService] Extracting improvements from JSON node");
        List<String> improvements = new ArrayList<>();

        if (improvementsNode.isArray()) {
            improvementsNode.forEach(improvement -> {
                String area = improvement.path("area").asText();
                String detail = improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s", area, detail));
            });
            log.debug("[ActivityAIService] Extracted {} improvements", improvements.size());
        } else {
            log.debug("[ActivityAIService] Improvements node is not an array or is missing");
        }

        if (improvements.isEmpty()) {
            log.debug("[ActivityAIService] No improvements found, using default");
        }

        return improvements.isEmpty() ?
                Collections.singletonList("No specific improvements provided") :
                improvements;

    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if (!analysisNode.path(key).isMissingNode()){
            fullAnalysis.append(prefix)
                    .append(" ")
                    .append(analysisNode.path(key).asText())
                    .append("\n\n");
            log.debug("[ActivityAIService] Added analysis section: {}", key);
        } else {
            log.debug("[ActivityAIService] Analysis section '{}' is missing or null", key);
        }
    }

    private String createPromptForActivity(Activity activity) {
        log.debug("[ActivityAIService] Creating prompt for activity: type={}, duration={}, calories={}",
                activity.getType(), activity.getDuration(), activity.getCaloriesBurned());

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
Additional Data: %s

(If you must clarify a missing value, include a 1-line inferred assumption inside the relevant analysis field.)
""",
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalData()
        );

        log.debug("[ActivityAIService] Prompt generated successfully, totalLength={} characters", prompt.length());
        return prompt;
    }
}