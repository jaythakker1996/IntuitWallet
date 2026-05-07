package com.intuit.walletservice.service.dto;

import com.intuit.walletservice.businesslogic.core.QrView;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QrResponse(
        UUID qrCodeId,
        UUID walletId,
        String payload,
        String type,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static QrResponse from(QrView view) {
        return new QrResponse(
                view.qrCodeId(),
                view.walletId(),
                view.payload(),
                view.type(),
                view.status(),
                view.expiresAt(),
                view.createdAt(),
                view.updatedAt());
    }
}
