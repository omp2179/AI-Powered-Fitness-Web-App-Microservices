package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class ActivityAIService {
    private final GeminiService geminiService;
    private static final String SERVICE_NAME = "[ActivityAIService]";

    public Recommendation generateRecommendation(Activity activity) {
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
            if (aiResponse == null) {
                log.warn("{} Received null AI response for activityId={}", SERVICE_NAME, activity.getId());
                return null;
            }
            String prettyAiResponse = aiResponse.replace("\\n", "\n");

            System.out.println();
            System.out.println();
            log.info("\n========== [AI RESPONSE START] ==========\n{}\n=========== [AI RESPONSE END] ==========\n", prettyAiResponse);
            System.out.println();
            System.out.println();
            long duration = System.currentTimeMillis() - startTime;

            log.info("{} ✓ Received AI response for activityId={}, responseLength={}, duration={}ms",
                    SERVICE_NAME, activity.getId(), aiResponse.length(), duration);
            log.debug("{} Response preview: {}", SERVICE_NAME,
                    aiResponse.length() > 300 ? aiResponse.substring(0, 300) + "..." : aiResponse);



            log.info("{} ✓ Successfully processed recommendation for activityId={}, userId={}",
                    SERVICE_NAME, activity.getId(), activity.getUserId());

            return processAIResponse(activity,aiResponse);

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

    private Recommendation processAIResponse(Activity activity, String aiResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(aiResponse);
            JsonNode textNode = rootNode.path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text");

            String jsonContent = textNode.asText().replaceAll("```json\\n","")
                    .replaceAll("\\n```","");

            JsonNode analysisJson = mapper.readTree(jsonContent);
            JsonNode analysisNode = analysisJson.path("analysis");
            StringBuilder fullAnalysis = new StringBuilder();

            addAnalysisSection(fullAnalysis,analysisNode,"overall","Overall:");
            addAnalysisSection(fullAnalysis,analysisNode,"pace","Pace:");
            addAnalysisSection(fullAnalysis,analysisNode,"heartRate","Heart Rate:");
            addAnalysisSection(fullAnalysis,analysisNode,"caloriesBurned","Calories:");

            List<String> improvements = extractImprovements(analysisJson.path("improvements"));
            List<String> suggestions = extractSuggestions(analysisJson.path("suggestions"));
            List<String> safety = extractSafetyGuidelines(analysisJson.path("safety"));

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .type(activity.getType().toString())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)

                    .safety(safety)
                    .build();

        }catch (Exception e) {
            log.warn("{} Failed to parse/process AI response for activityId={}: {}",
                    SERVICE_NAME, activity.getId(), e.getMessage(), e);

            return createDefaultRecommendation(activity);
        }

    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .type(activity.getType().toString())
                .recommendation("Unable to generate detailed recommendation at this time.")
                .improvements(Collections.singletonList("No specific improvements suggested."))
                .suggestions(Collections.singletonList("No specific workout suggestions provided."))
                .safety(List.of("No specific safety guidelines provided."))
                .build();
    }

    private List<String> extractSafetyGuidelines(JsonNode safetyNode) {
        List<String> safetyGuidelines = new ArrayList<>();
        if(safetyNode.isArray()) {
            safetyNode.forEach(item->
                safetyGuidelines.add(item.asText())
            );
        }
        return safetyGuidelines.isEmpty() ? Collections.singletonList("No specific safety guidelines provided.") : safetyGuidelines;
    }

    private List<String> extractSuggestions(JsonNode suggestionsNode) {
        List<String> suggestions = new ArrayList<>();
        if (suggestionsNode.isArray()) {
            suggestionsNode.forEach(suggestion -> {
                String workout = suggestion.path("workout").asText();
                String description = suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s", workout, description));
            });
        }

        return suggestions.isEmpty() ? Collections.singletonList("No specific workout suggestions provided.") : suggestions;
    }

    private List<String> extractImprovements(JsonNode improvementsNode) {
        List<String> improvements = new ArrayList<>();
        if(improvementsNode.isArray()) {
            improvementsNode.forEach(improvement->{
                String area = improvement.path("area").asText();
                String recommendation = improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s",area,recommendation));
            });
        }
        return improvements.isEmpty() ? Collections.singletonList("No specific improvements suggested.") : improvements;
    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if(!analysisNode.path(key).isMissingNode()) {
            fullAnalysis.append(prefix).append(analysisNode.path(key).asText()).append("\n\n");
        }
    }


    private String createPromptForActivity(Activity activity) {
        log.debug("{} Creating prompt for activity: type={}, duration={}, calories={}",
                 SERVICE_NAME, activity.getType(), activity.getDuration(), activity.getCaloriesBurned());

        // String.format will throw if the number/types of placeholders don't match.
        // Keep the placeholders aligned and make values null-safe.
        String activityType = String.valueOf(activity.getType());
        int durationMinutes = activity.getDuration() == null ? 0 : activity.getDuration();
        int caloriesBurned = activity.getCaloriesBurned() == null ? 0 : activity.getCaloriesBurned();
        String additionalMetrics = "N/A";

        String prompt = String.format("""
  You are an Elite Sports Physiologist and Senior Exercise Scientist analyzing workouts for a daily-use fitness app.
 Your response should feel like guidance from a friendly, experienced personal trainer: correct, clear, detailed, encouraging, and practical.

━━━━━━━━━━━━━━━━━━━━━━
ABSOLUTE OUTPUT RULES
━━━━━━━━━━━━━━━━━━━━━━
1. Output MUST be exactly ONE valid raw JSON object — nothing else.
2. Do NOT include markdown, explanations, comments, or extra text.
3. Use double quotes for all strings and valid JSON only.
4. Do NOT add, remove, rename, or reorder any keys.
5. The output must be accurate, logically consistent, and easy to understand for everyday users.

━━━━━━━━━━━━━━━━━━━━━━
REQUIRED JSON STRUCTURE (MUST MATCH EXACTLY)
━━━━━━━━━━━━━━━━━━━━━━
{
  "analysis": {
    "overall": "Overall effort analysis with a brief numeric rationale (e.g. calories per minute).",
    "pace": "Pace or tempo analysis with a clear coaching cue.",
    "heartRate": "Estimated heart-rate zone or effort level with a simple explanation.",
    "caloriesBurned": "Interpretation of calorie burn and its training impact."
  },
  "improvements": [
    {
      "area": "Area name",
      "recommendation": "Clear, detailed, and actionable recommendation written in simple language"
    }
  ],
  "suggestions": [
    {
      "workout": "Workout name",
      "description": "Detailed, step-by-step workout description including duration, intensity, reps, or pace"
    }
  ],
  "safety": [
    "Short, clear safety guideline written for everyday users",
    "Another concise and practical safety guideline"
  ]
}

━━━━━━━━━━━━━━━━━━━━━━
CONTENT & QUALITY GUIDELINES
━━━━━━━━━━━━━━━━━━━━━━
• Be CORRECT: base all conclusions on the provided data; avoid contradictions.
• Be CLEAR: avoid vague advice — every point should be immediately understandable.
• Be DETAILED: explain what to do, how long, how hard, and why it helps.
• Include at least one numeric insight where useful (e.g., cal/min, pace, duration).
• Use plain English; if technical terms appear, briefly explain them.

━━━━━━━━━━━━━━━━━━━━━━
UX WRITING RULES (CRITICAL)
━━━━━━━━━━━━━━━━━━━━━━
• Use encouraging micro-language where appropriate:
  - Examples: "Nice work — small tweak: ...", "Quick tip: ...", "Good consistency here, next step: ..."
• Make EVERY recommendation actionable and measurable:
  - Include numbers such as minutes, reps, pace, heart-rate zone, cadence, or effort level (RPE).
• Prioritize clarity for recreational users new to training:
  - Avoid assumptions of prior fitness knowledge.
  - Prefer simple cues like "comfortable pace", "slightly out of breath", "able to talk in short sentences".
• Write in a positive, motivating tone — focus on progress, not faults.
• Assume the output will be shown directly in the app UI without edits.

━━━━━━━━━━━━━━━━━━━━━━
INPUT DATA TO ANALYZE
━━━━━━━━━━━━━━━━━━━━━━
Activity Type: %s
Duration: %d minutes
Calories Burned: %d
Additional Metrics: %s

━━━━━━━━━━━━━━━━━━━━━━
FINAL INSTRUCTION
━━━━━━━━━━━━━━━━━━━━━━
Analyze the activity focusing on performance, improvement areas, next workout suggestions, and safety.
Return ONLY the JSON object in the exact format specified above.
""", activityType, durationMinutes, caloriesBurned, additionalMetrics);

        log.debug("{} Prompt generated successfully, totalLength={} characters", SERVICE_NAME, prompt.length());
        return prompt;
    }
}
