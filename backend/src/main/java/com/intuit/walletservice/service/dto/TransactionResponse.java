package com.intuit.walletservice.service.dto;

import com.intuit.walletservice.businesslogic.core.TransactionView;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID txId,
        String type,
        UUID fromWalletId,
        UUID toWalletId,
        BigDecimal amount,
        String stablecoin,
        BigDecimal fee,
        String status,
        OffsetDateTime createdAt) {

    public static TransactionResponse from(TransactionView view) {
        return new TransactionResponse(
                view.txId(),
                view.type(),
                UUID.fromString(view.fromParty()),
                UUID.fromString(view.toParty()),
                view.amount(),
                view.stablecoin(),
                view.fee(),
                view.status(),
                view.createdAt());
    }
}
