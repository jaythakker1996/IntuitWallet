package com.intuit.walletservice.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PingRequest(
        @NotBlank
        @Size(max = 255)
        String message) {
}
