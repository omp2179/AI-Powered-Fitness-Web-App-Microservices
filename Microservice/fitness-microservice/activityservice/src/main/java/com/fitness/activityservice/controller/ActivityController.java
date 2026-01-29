package com.fitness.activityservice.controller;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.service.ActivityService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
@AllArgsConstructor
@Slf4j
public class ActivityController {

    @Autowired
    private ActivityService activityService;


    @PostMapping
    public ResponseEntity<?> trackActivity(@Valid @RequestBody ActivityRequest request, BindingResult br) {
        if (br.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            List<FieldError> fieldErrors = br.getFieldErrors();
            fieldErrors.forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
            log.warn("Validation failed for incoming activity: {}", errors);
            return ResponseEntity.badRequest().body(errors);
        }

        long start = System.currentTimeMillis();
        log.info("[HTTP POST] /api/activities userId={}, type={}, duration={}", request.getUserId(), request.getType(), request.getDuration());
        ActivityResponse response = activityService.trackActivity(request);
        log.info("[HTTP 200] /api/activities completed in {} ms, activityId={}", (System.currentTimeMillis()-start), response.getId());
        return ResponseEntity.ok(response);
    }
}
