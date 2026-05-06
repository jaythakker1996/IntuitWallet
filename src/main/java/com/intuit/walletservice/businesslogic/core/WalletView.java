package com.intuit.walletservice.businesslogic.core;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletView(
        UUID walletId,
        UUID intuitAccountId,
        String type,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
