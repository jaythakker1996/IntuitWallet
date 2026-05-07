package com.intuit.walletservice.businesslogic.core;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionView(
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
        OffsetDateTime createdAt) {
}
