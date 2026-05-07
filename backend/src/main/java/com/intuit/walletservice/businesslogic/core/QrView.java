package com.intuit.walletservice.businesslogic.core;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QrView(
        UUID qrCodeId,
        UUID walletId,
        String payload,
        String type,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
