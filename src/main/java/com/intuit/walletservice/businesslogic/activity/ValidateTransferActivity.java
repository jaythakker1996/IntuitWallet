package com.intuit.walletservice.businesslogic.activity;

import io.temporal.activity.ActivityInterface;

import java.util.UUID;

@ActivityInterface
public interface ValidateTransferActivity {

    void validateTransfer(UUID fromWalletId, UUID toWalletId);
}
