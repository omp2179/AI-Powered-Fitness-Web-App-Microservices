package com.fitness.aiservice.controller;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.service.RecommandationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
@Slf4j
public class RecommendationController {
    private final RecommandationService recommandationService;


//    public String test() {
//        return "Hello from Recommendation Service!";
//    }
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Recommendation>> getUserRecommendation(@PathVariable String userId) {
        long start = System.currentTimeMillis();
        log.info("[HTTP GET] /api/recommendations/user/{}", userId);
        List<Recommendation> result = recommandationService.getUserRecommendation(userId);
        log.info("[HTTP 200] /api/recommendations/user/{} -> {} items ({} ms)", userId, result.size(), (System.currentTimeMillis()-start));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<List<Recommendation>> getActivityRecommendation(@PathVariable String activityId) {
        long start = System.currentTimeMillis();
        log.info("[HTTP GET] /api/recommendations/activity/{}", activityId);
        List<Recommendation> result = recommandationService.getActivityRecommendation(activityId);
        log.info("[HTTP 200] /api/recommendations/activity/{} -> {} items ({} ms)", activityId, result.size(), (System.currentTimeMillis()-start));
        return ResponseEntity.ok(result);
    }
}
