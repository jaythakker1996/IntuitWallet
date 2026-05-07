package com.intuit.walletservice.service.dto;

import com.intuit.walletservice.businesslogic.core.WalletTransactionView;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletTransactionResponse(
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

    public static WalletTransactionResponse from(WalletTransactionView view) {
        return new WalletTransactionResponse(
                view.txId(),
                view.type(),
                view.direction(),
                view.entryType(),
                view.fromWalletId(),
                view.toWalletId(),
                view.amount(),
                view.stablecoin(),
                view.fee(),
                view.status(),
                view.runningAvailableAfter(),
                view.runningPendingAfter(),
                view.entrySequence(),
                view.createdAt());
    }
}
