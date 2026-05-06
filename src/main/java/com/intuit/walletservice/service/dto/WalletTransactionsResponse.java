package com.intuit.walletservice.service.dto;

import com.intuit.walletservice.businesslogic.core.WalletTransactionView;

import java.util.List;
import java.util.UUID;

public record WalletTransactionsResponse(
        UUID walletId,
        List<WalletTransactionResponse> transactions) {

    public static WalletTransactionsResponse from(UUID walletId, List<WalletTransactionView> views) {
        return new WalletTransactionsResponse(
                walletId,
                views.stream().map(WalletTransactionResponse::from).toList());
    }
}
