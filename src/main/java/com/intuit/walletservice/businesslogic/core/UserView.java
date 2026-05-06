package com.intuit.walletservice.businesslogic.core;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserView(
        UUID intuitAccountId,
        String email,
        String role,
        String homeRegion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
