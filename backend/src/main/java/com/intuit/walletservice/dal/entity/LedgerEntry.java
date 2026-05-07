package com.intuit.walletservice.dal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Column(name = "tx_id", nullable = false, updatable = false)
    private UUID txId;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "stablecoin", nullable = false, updatable = false)
    private String stablecoin;

    @Column(name = "entry_type", nullable = false, updatable = false)
    private String entryType;

    @Column(name = "amount", nullable = false, updatable = false, precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(name = "running_available", nullable = false, updatable = false, precision = 28, scale = 8)
    private BigDecimal runningAvailable;

    @Column(name = "running_pending", nullable = false, updatable = false, precision = 28, scale = 8)
    private BigDecimal runningPending;

    @Column(name = "entry_sequence", nullable = false, updatable = false)
    private Long entrySequence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(
            UUID entryId,
            UUID txId,
            UUID walletId,
            String stablecoin,
            String entryType,
            BigDecimal amount,
            BigDecimal runningAvailable,
            BigDecimal runningPending,
            long entrySequence) {
        this.entryId = entryId;
        this.txId = txId;
        this.walletId = walletId;
        this.stablecoin = stablecoin;
        this.entryType = entryType;
        this.amount = amount;
        this.runningAvailable = runningAvailable;
        this.runningPending = runningPending;
        this.entrySequence = entrySequence;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getTxId() {
        return txId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public String getStablecoin() {
        return stablecoin;
    }

    public String getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getRunningAvailable() {
        return runningAvailable;
    }

    public BigDecimal getRunningPending() {
        return runningPending;
    }

    public Long getEntrySequence() {
        return entrySequence;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
