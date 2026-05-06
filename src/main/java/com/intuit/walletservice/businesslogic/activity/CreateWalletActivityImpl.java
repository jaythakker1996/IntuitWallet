package com.intuit.walletservice.businesslogic.activity;

import com.intuit.walletservice.businesslogic.core.WalletCoreService;
import com.intuit.walletservice.businesslogic.core.WalletCoreService.CreateWalletResult;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ActivityImpl(workers = "wallet-service-worker")
public class CreateWalletActivityImpl implements CreateWalletActivity {

    private final WalletCoreService walletCoreService;

    public CreateWalletActivityImpl(WalletCoreService walletCoreService) {
        this.walletCoreService = walletCoreService;
    }

    @Override
    public CreateWalletResult createIfMissing(UUID intuitAccountId) {
        return walletCoreService.createIfMissing(intuitAccountId);
    }
}
