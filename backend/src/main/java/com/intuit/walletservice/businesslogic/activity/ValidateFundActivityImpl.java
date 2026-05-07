package com.intuit.walletservice.businesslogic.activity;

import com.intuit.walletservice.businesslogic.core.WalletCoreService;
import com.intuit.walletservice.businesslogic.core.WalletNotActiveException;
import com.intuit.walletservice.businesslogic.core.WalletView;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ActivityImpl(workers = "wallet-service-worker")
public class ValidateFundActivityImpl implements ValidateFundActivity {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String TYPE_USER = "USER";

    private final WalletCoreService walletCoreService;

    public ValidateFundActivityImpl(WalletCoreService walletCoreService) {
        this.walletCoreService = walletCoreService;
    }

    @Override
    public void validateFund(UUID targetWalletId) {
        WalletView target = walletCoreService.getById(targetWalletId);
        if (!STATUS_ACTIVE.equals(target.status())) {
            throw new WalletNotActiveException(
                    "Target wallet not ACTIVE: " + target.walletId() + " (status=" + target.status() + ")");
        }
        if (!TYPE_USER.equals(target.type())) {
            throw new IllegalArgumentException(
                    "Fund target must be type=USER: " + target.walletId() + " (type=" + target.type() + ")");
        }
    }
}
