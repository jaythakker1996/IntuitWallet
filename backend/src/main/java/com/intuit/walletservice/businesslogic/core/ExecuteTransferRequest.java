package com.intuit.walletservice.businesslogic.core;

import java.math.BigDecimal;
import java.util.UUID;

public record ExecuteTransferRequest(
        String txType,
        UUID fromWalletId,
        UUID toWalletId,
        BigDecimal amount,
        String stablecoin,
        String idempotencyKey,
        String requestHash) {
}
