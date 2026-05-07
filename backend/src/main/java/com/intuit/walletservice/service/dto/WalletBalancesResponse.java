package com.intuit.walletservice.service.dto;

import com.intuit.walletservice.businesslogic.core.WalletBalanceView;

import java.util.List;
import java.util.UUID;

public record WalletBalancesResponse(UUID walletId, List<Entry> balances) {

    public static WalletBalancesResponse from(UUID walletId, List<WalletBalanceView> views) {
        return new WalletBalancesResponse(walletId, views.stream().map(Entry::from).toList());
    }

    public record Entry(
            String stablecoin,
            java.math.BigDecimal runningAvailable,
            java.math.BigDecimal runningPending,
            long lastEntrySequence,
            java.time.OffsetDateTime lastEntryAt) {

        static Entry from(WalletBalanceView view) {
            return new Entry(
                    view.stablecoin(),
                    view.runningAvailable(),
                    view.runningPending(),
                    view.lastEntrySequence(),
                    view.lastEntryAt());
        }
    }
}
