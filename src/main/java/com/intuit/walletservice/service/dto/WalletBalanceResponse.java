package com.intuit.walletservice.service.dto;

import com.intuit.walletservice.businesslogic.core.WalletBalanceView;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletBalanceResponse(
        UUID walletId,
        String stablecoin,
        BigDecimal runningAvailable,
        BigDecimal runningPending,
        long lastEntrySequence,
        OffsetDateTime lastEntryAt) {

    public static WalletBalanceResponse from(WalletBalanceView view) {
        return new WalletBalanceResponse(
                view.walletId(),
                view.stablecoin(),
                view.runningAvailable(),
                view.runningPending(),
                view.lastEntrySequence(),
                view.lastEntryAt());
    }
}
