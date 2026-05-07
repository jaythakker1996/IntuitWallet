package com.intuit.walletservice.businesslogic.activity;

import com.intuit.walletservice.businesslogic.core.WalletCoreService.CreateWalletResult;
import io.temporal.activity.ActivityInterface;

import java.util.UUID;

@ActivityInterface
public interface CreateWalletActivity {

    CreateWalletResult createIfMissing(UUID intuitAccountId);
}
