package com.intuit.walletservice.businesslogic.core;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletBalanceView(
        UUID walletId,
        String stablecoin,
        BigDecimal runningAvailable,
        BigDecimal runningPending,
        long lastEntrySequence,
        OffsetDateTime lastEntryAt) {
}
