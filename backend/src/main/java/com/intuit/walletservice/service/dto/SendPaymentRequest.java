package com.intuit.walletservice.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record SendPaymentRequest(
        @NotNull UUID fromWalletId,
        @NotNull UUID toWalletId,
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 20, fraction = 8)
        BigDecimal amount,
        @NotBlank String stablecoin,
        @NotBlank @Size(max = 64) String idempotencyKey) {
}
