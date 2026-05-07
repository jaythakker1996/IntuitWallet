package com.intuit.walletservice.service.controller;

import com.intuit.walletservice.businesslogic.core.UserCoreService;
import com.intuit.walletservice.businesslogic.core.UserCoreService.CreateUserResult;
import com.intuit.walletservice.service.dto.CreateUserRequest;
import com.intuit.walletservice.service.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserCoreService userCoreService;

    public UserController(UserCoreService userCoreService) {
        this.userCoreService = userCoreService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        CreateUserResult result = userCoreService.createUser(
                request.email(),
                request.role().name(),
                request.homeRegion());
        UserResponse body = UserResponse.from(result.user());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/{intuitAccountId}")
    public UserResponse get(@PathVariable UUID intuitAccountId) {
        return UserResponse.from(userCoreService.getUser(intuitAccountId));
    }
}
