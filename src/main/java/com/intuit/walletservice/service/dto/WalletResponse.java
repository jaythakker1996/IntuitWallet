package com.intuit.walletservice.service.dto;

import com.intuit.walletservice.businesslogic.core.WalletView;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletResponse(
        UUID walletId,
        UUID intuitAccountId,
        String type,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static WalletResponse from(WalletView view) {
        return new WalletResponse(
                view.walletId(),
                view.intuitAccountId(),
                view.type(),
                view.status(),
                view.createdAt(),
                view.updatedAt());
    }
}
