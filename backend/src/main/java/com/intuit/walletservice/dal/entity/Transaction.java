package com.intuit.walletservice.dal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @Column(name = "tx_id", nullable = false, updatable = false)
    private UUID txId;

    @Column(name = "type", nullable = false, updatable = false)
    private String type;

    @Column(name = "from_type", nullable = false, updatable = false)
    private String fromType;

    @Column(name = "from_party", nullable = false, updatable = false)
    private String fromParty;

    @Column(name = "to_type", nullable = false, updatable = false)
    private String toType;

    @Column(name = "to_party", nullable = false, updatable = false)
    private String toParty;

    @Column(name = "stablecoin", nullable = false, updatable = false)
    private String stablecoin;

    @Column(name = "amount", nullable = false, updatable = false, precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(name = "fee", nullable = false, updatable = false, precision = 28, scale = 8)
    private BigDecimal fee;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "blockchain_tx_hash", updatable = false)
    private String blockchainTxHash;

    @Column(name = "compliance_check_id", updatable = false)
    private UUID complianceCheckId;

    @Column(name = "batch_id", updatable = false)
    private UUID batchId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Transaction() {
    }

    public Transaction(
            UUID txId,
            String type,
            String fromType,
            String fromParty,
            String toType,
            String toParty,
            String stablecoin,
            BigDecimal amount,
            BigDecimal fee,
            String status,
            String idempotencyKey,
            String requestHash) {
        this.txId = txId;
        this.type = type;
        this.fromType = fromType;
        this.fromParty = fromParty;
        this.toType = toType;
        this.toParty = toParty;
        this.stablecoin = stablecoin;
        this.amount = amount;
        this.fee = fee;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getTxId() {
        return txId;
    }

    public String getType() {
        return type;
    }

    public String getFromType() {
        return fromType;
    }

    public String getFromParty() {
        return fromParty;
    }

    public String getToType() {
        return toType;
    }

    public String getToParty() {
        return toParty;
    }

    public String getStablecoin() {
        return stablecoin;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public String getStatus() {
        return status;
    }

    public String getBlockchainTxHash() {
        return blockchainTxHash;
    }

    public UUID getComplianceCheckId() {
        return complianceCheckId;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
