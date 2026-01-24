package com.fitness.userservice.controller;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.service.Usersevice;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RestController
@RequestMapping("/api/users")
@AllArgsConstructor
public class Usercontroller {
    private static final Logger logger = LoggerFactory.getLogger(Usercontroller.class);
    private Usersevice userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(@PathVariable String userId) {
        logger.info("Fetching user profile for userId: {}", userId);
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Registering user with email: {}", request.getEmail());
        return ResponseEntity.ok(userService.register(request));
    }

    @GetMapping("/{userId}/validate")
    public ResponseEntity<Boolean> validateUser(@PathVariable String userId) {
        logger.info("Validating user existence for userId: {}", userId);
        return ResponseEntity.ok(userService.existByKeycloakId(userId));
    }

    @GetMapping("/debug/all-ids")
    public ResponseEntity<?> getAllUserIds() {
        logger.info("Fetching all user IDs for debugging");
        return ResponseEntity.ok(userService.getAllUserIds());
    }
}
