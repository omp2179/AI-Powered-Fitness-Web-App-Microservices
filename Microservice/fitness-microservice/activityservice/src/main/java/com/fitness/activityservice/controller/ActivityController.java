package com.fitness.activityservice.controller;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.service.ActivityService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
@AllArgsConstructor
@Slf4j
public class ActivityController {

    @Autowired
    private ActivityService activityService;


    @PostMapping
    public ResponseEntity<ActivityResponse> trackActivity(@RequestBody ActivityRequest request) {
        long start = System.currentTimeMillis();
        log.info("[HTTP POST] /api/activities userId={}, type={}, duration={}", request.getUserId(), request.getType(), request.getDuration());
        ActivityResponse response = activityService.trackActivity(request);
        log.info("[HTTP 200] /api/activities completed in {} ms, activityId={}", (System.currentTimeMillis()-start), response.getId());
        return ResponseEntity.ok(response);

    }
}
