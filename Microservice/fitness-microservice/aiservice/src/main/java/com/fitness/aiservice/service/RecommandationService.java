package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommandationService {
    private final RecommendationRepository recommendationRepository;

    public List<Recommendation> getUserRecommendation(String userId) {
        log.info("[RecommendationService] getUserRecommendation userId={}", userId);
        List<Recommendation> list = recommendationRepository.findByUserId(userId);
        log.info("[RecommendationService] userId={} -> {} items", userId, list.size());
        return list;
    }

    public List<Recommendation> getActivityRecommendation(String activityId) {
        log.info("[RecommendationService] getActivityRecommendation activityId={}", activityId);
        Recommendation rec = recommendationRepository.findByActivityId(activityId)
                .orElseThrow(() -> new RuntimeException("Recommendation not found for activityId: " + activityId));
        log.info("[RecommendationService] activityId={} -> 1 item", activityId);
        return Collections.singletonList(rec);
    }
}
