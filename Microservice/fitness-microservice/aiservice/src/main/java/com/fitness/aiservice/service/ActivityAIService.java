package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.ActivityRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class ActivityAIService {
    private final GeminiService geminiService;
    private final ActivityRepository activityRepository;
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
            String currentActivityId = activity.getId();

            // Step 2: Fetch last 7 days activities from LOCAL MongoDB (via Kafka events)
            log.info("{} Querying local MongoDB for last 7 days activities for userId={}",
                    SERVICE_NAME, activity.getUserId());
            LocalDateTime queryAnchor = activity.getStartTime() != null ? activity.getStartTime() : LocalDateTime.now();
            LocalDateTime end = queryAnchor;
            LocalDateTime start = end.minusDays(7);

            log.debug("{} Query parameters: userId={}, start={}, end={}",
                    SERVICE_NAME, activity.getUserId(), start, end);
            log.debug("{} Current activity: id={}, startTime={}, type={}, duration={}",
                    SERVICE_NAME, activity.getId(), activity.getStartTime(),
                    activity.getType(), activity.getDuration());

            // Check total activities in DB for this user (without time filter)
            List<Activity> allUserActivities = activityRepository.findAll().stream()
                    .filter(a -> a.getUserId().equals(activity.getUserId()))
                    .toList();
            log.info("{} Total activities for userId={} in DB (no time filter): {}",
                    SERVICE_NAME, activity.getUserId(), allUserActivities.size());

            if (!allUserActivities.isEmpty()) {
                log.debug("{} Sample activities for userId={}:", SERVICE_NAME, activity.getUserId());
                allUserActivities.stream().limit(5).forEach(a ->
                    log.debug("  - Activity: id={}, startTime={}, type={}, duration={}",
                            a.getId(), a.getStartTime(), a.getType(), a.getDuration()));
            }

            List<Activity> last7DaysActivities = activityRepository.findByUserIdAndStartTimeBetween(
                    activity.getUserId(), start, end)
                    .stream()
                    .filter(a -> currentActivityId == null || !currentActivityId.equals(a.getId()))
                    .collect(Collectors.toList());

            log.info("{} Retrieved {} activities from last 7 days (local DB) for userId={}",
                    SERVICE_NAME, last7DaysActivities.size(), activity.getUserId());

            if (last7DaysActivities.isEmpty()) {
                log.warn("{} ⚠ No activities found in 7-day query!", SERVICE_NAME);
                log.warn("{} Possible reasons: 1) startTime is null/invalid 2) startTime outside 7-day window 3) userId mismatch",
                        SERVICE_NAME);
                log.warn("{} Current time: {}, Query range: {} to {}",
                        SERVICE_NAME, LocalDateTime.now(), start, end);
                log.info("{} Falling back to last 7 recorded activities for userId={} to build AI context",
                        SERVICE_NAME, activity.getUserId());
                last7DaysActivities = activityRepository.findTop7ByUserIdOrderByStartTimeDesc(activity.getUserId())
                        .stream()
                        .filter(a -> currentActivityId == null || !currentActivityId.equals(a.getId()))
                        .collect(Collectors.toList());
                log.info("{} Fallback retrieved {} activities", SERVICE_NAME, last7DaysActivities.size());
            } else {
                log.debug("{} Sample activity startTimes from query result:", SERVICE_NAME);
                last7DaysActivities.stream().limit(3).forEach(a ->
                    log.debug("  - Activity id={}, startTime={}", a.getId(), a.getStartTime()));
            }

            // Step 3: Create enriched prompt with 7-day context
            log.info("{} Creating enriched prompt with 7-day context for activity type: {}",
                    SERVICE_NAME, activity.getType());
            String prompt = createEnrichedPromptWithContext(activity, last7DaysActivities);
            log.info("{} Prompt created successfully, length={} characters", SERVICE_NAME, prompt.length());
            log.debug("{} Prompt preview: {}", SERVICE_NAME,
                    prompt.length() > 300 ? prompt.substring(0, 300) + "..." : prompt);

            // Step 4: Request recommendations from Gemini
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

    private String createEnrichedPromptWithContext(Activity activity, List<Activity> last7DaysActivities) {
        log.debug("{} Creating enriched prompt with 7-day context for activity: type={}, duration={}, calories={}",
                 SERVICE_NAME, activity.getType(), activity.getDuration(), activity.getCaloriesBurned());

        // Build 7-day activity summary
        String activityContext = buildActivityContextSummary(last7DaysActivities);

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
USER'S 7-DAY ACTIVITY HISTORY
━━━━━━━━━━━━━━━━━━━━━━
%s

━━━━━━━━━━━━━━━━━━━━━━
CURRENT ACTIVITY DATA TO ANALYZE
━━━━━━━━━━━━━━━━━━━━━━
Activity Type: %s
Duration: %d minutes
Calories Burned: %d
Additional Metrics: %s

━━━━━━━━━━━━━━━━━━━━━━
CONTEXT-AWARE ANALYSIS REQUIREMENTS
━━━━━━━━━━━━━━━━━━━━━━
Based on the 7-day activity history provided above, your analysis should:
1. Identify which muscle groups or cardio types have been NEGLECTED in the past week
2. Assess whether the user needs REST or ACTIVE RECOVERY based on frequency and intensity
3. Evaluate BALANCE and PERIODIZATION of their routine
4. Suggest PROGRESSIVE OVERLOAD opportunities where appropriate
5. Recommend TODAY'S workout that COMPLEMENTS their recent activity pattern (not duplicate)
6. If the user has been inactive, provide gentle encouragement to restart
7. If the user is overtraining specific areas, suggest variety or rest

━━━━━━━━━━━━━━━━━━━━━━
FINAL INSTRUCTION
━━━━━━━━━━━━━━━━━━━━━━
Analyze the current activity in the CONTEXT of their 7-day pattern. Focus on performance, improvement areas,
next workout suggestions that complement their history, and safety considerations based on their recent training load.
Return ONLY the JSON object in the exact format specified above.
""", activityContext, activityType, durationMinutes, caloriesBurned, additionalMetrics);

        log.debug("{} Enriched prompt generated successfully, totalLength={} characters", SERVICE_NAME, prompt.length());
        return prompt;
    }

    private String buildActivityContextSummary(List<Activity> activities) {
        if (activities == null || activities.isEmpty()) {
            return "No activities recorded in the last 7 days. This is the user's first activity or they've been inactive.";
        }

        Map<String, Integer> typeCount = new HashMap<>();
        Map<String, Integer> typeTotalMinutes = new HashMap<>();
        int totalMinutes = 0;
        int totalCalories = 0;

        for (Activity a : activities) {
            String type = a.getType().toString();
            int duration = a.getDuration() != null ? a.getDuration() : 0;
            int calories = a.getCaloriesBurned() != null ? a.getCaloriesBurned() : 0;

            typeCount.merge(type, 1, Integer::sum);
            typeTotalMinutes.merge(type, duration, Integer::sum);
            totalMinutes += duration;
            totalCalories += calories;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("LAST 7 DAYS SUMMARY:\n");
        summary.append(String.format("- Total activities: %d\n", activities.size()));
        summary.append(String.format("- Total minutes exercised: %d\n", totalMinutes));
        summary.append(String.format("- Total calories burned: %d\n", totalCalories));
        summary.append(String.format("- Average duration per session: %d minutes\n", totalMinutes / activities.size()));
        summary.append("\nBREAKDOWN BY ACTIVITY TYPE:\n");

        typeCount.forEach((type, count) -> {
            int minutes = typeTotalMinutes.getOrDefault(type, 0);
            summary.append(String.format("  • %s: %d session(s), %d total minutes\n", type, count, minutes));
        });

        return summary.toString();
    }
}
