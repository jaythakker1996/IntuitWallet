package com.intuit.walletservice.dal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "qr_codes")
public class Qr {

    @Id
    @Column(name = "qr_code_id", nullable = false, updatable = false)
    private UUID qrCodeId;

    @Column(name = "wallet_id", nullable = false, updatable = false, unique = true)
    private UUID walletId;

    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Qr() {
    }

    public Qr(UUID qrCodeId, UUID walletId, String payload, String type, String status) {
        this.qrCodeId = qrCodeId;
        this.walletId = walletId;
        this.payload = payload;
        this.type = type;
        this.status = status;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getQrCodeId() {
        return qrCodeId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public String getPayload() {
        return payload;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
