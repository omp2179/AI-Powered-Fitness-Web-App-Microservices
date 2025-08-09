package com.fitness.userservice.service;


import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

@Service
public class Usersevice {
    public UserResponse register(@Valid RegisterRequest request) {

    }
}
