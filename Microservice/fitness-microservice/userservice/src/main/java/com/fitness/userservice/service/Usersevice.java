package com.fitness.userservice.service;


import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User;
import com.fitness.userservice.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class Usersevice {

    @Autowired
    private UserRepository repository;

    public UserResponse register(@Valid RegisterRequest request) {
        log.info("Attempting to register user with email: {}", request.getEmail());
        try {
            if(repository.existsByEmail((request.getEmail()))) {
                log.warn("Registration failed: Email already exists - {}", request.getEmail());
                throw new RuntimeException("Email already exists");
            }
            User user = new User();
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());
            user.setFirstname(request.getFirstName());
            user.setLastname(request.getLastName());
            User savedUser=repository.save(user);
            log.info("User registered successfully: {}", savedUser.getEmail());
            UserResponse userResponse = new UserResponse();
            userResponse.setId(savedUser.getId());
            userResponse.setPassword(savedUser.getPassword());
            userResponse.setEmail(savedUser.getEmail());
            userResponse.setFirstname(savedUser.getFirstname());
            userResponse.setLastname(savedUser.getLastname());
            userResponse.setCreatedAt(savedUser.getCreatedAt());
            userResponse.setUpdatedAt(savedUser.getUpdatedAt());
            return userResponse;
        } catch (Exception e) {
            log.error("Exception during registration for email: {}", request.getEmail(), e);
            throw e;
        }
    }

    public UserResponse getUserProfile(String userId) {
        log.info("Fetching profile for userId: {}", userId);
        try {
            User user = repository.findById(userId)
                    .orElseThrow(() -> {
                        log.warn("User not found for userId: {}", userId);
                        return new RuntimeException("User not found");
                    });
            UserResponse userResponse = new UserResponse();
            userResponse.setId(user.getId());
            userResponse.setPassword(user.getPassword());
            userResponse.setEmail(user.getEmail());
            userResponse.setFirstname(user.getFirstname());
            userResponse.setLastname(user.getLastname());
            userResponse.setCreatedAt(user.getCreatedAt());
            userResponse.setUpdatedAt(user.getUpdatedAt());
            log.info("Profile fetched for userId: {}", userId);
            return userResponse;
        } catch (Exception e) {
            log.error("Exception fetching profile for userId: {}", userId, e);
            throw e;
        }
    }

    public Boolean existByUserId(String userId) {
        log.info("Validating UserId: "+userId);
        return repository.existsById(userId);
    }
}
