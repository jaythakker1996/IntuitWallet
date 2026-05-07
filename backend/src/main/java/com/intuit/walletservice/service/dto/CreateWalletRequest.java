package com.intuit.walletservice.service.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWalletRequest(
        @NotNull UUID intuitAccountId) {
}
