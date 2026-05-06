package com.intuit.walletservice.businesslogic.core;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletTransactionView(
        UUID txId,
        String type,
        String direction,
        String entryType,
        UUID fromWalletId,
        UUID toWalletId,
        BigDecimal amount,
        String stablecoin,
        BigDecimal fee,
        String status,
        BigDecimal runningAvailableAfter,
        BigDecimal runningPendingAfter,
        long entrySequence,
        OffsetDateTime createdAt) {
}
