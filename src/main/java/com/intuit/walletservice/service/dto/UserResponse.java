package com.intuit.walletservice.service.dto;

import com.intuit.walletservice.businesslogic.core.UserView;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
        UUID intuitAccountId,
        String email,
        String role,
        String homeRegion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(UserView view) {
        return new UserResponse(
                view.intuitAccountId(),
                view.email(),
                view.role(),
                view.homeRegion(),
                view.createdAt(),
                view.updatedAt());
    }
}
