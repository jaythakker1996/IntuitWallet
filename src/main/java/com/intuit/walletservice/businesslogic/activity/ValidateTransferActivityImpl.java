package com.intuit.walletservice.businesslogic.activity;

import com.intuit.walletservice.businesslogic.core.WalletCoreService;
import com.intuit.walletservice.businesslogic.core.WalletNotActiveException;
import com.intuit.walletservice.businesslogic.core.WalletView;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ActivityImpl(workers = "wallet-service-worker")
public class ValidateTransferActivityImpl implements ValidateTransferActivity {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String TYPE_USER = "USER";

    private final WalletCoreService walletCoreService;

    public ValidateTransferActivityImpl(WalletCoreService walletCoreService) {
        this.walletCoreService = walletCoreService;
    }

    @Override
    public void validateTransfer(UUID fromWalletId, UUID toWalletId) {
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("fromWalletId and toWalletId must differ");
        }
        WalletView from = walletCoreService.getById(fromWalletId);
        WalletView to = walletCoreService.getById(toWalletId);
        requireActiveUser(from, "Sender");
        requireActiveUser(to, "Receiver");
    }

    private static void requireActiveUser(WalletView w, String role) {
        if (!STATUS_ACTIVE.equals(w.status())) {
            throw new WalletNotActiveException(
                    role + " wallet not ACTIVE: " + w.walletId() + " (status=" + w.status() + ")");
        }
        if (!TYPE_USER.equals(w.type())) {
            throw new IllegalArgumentException(
                    role + " wallet must be type=USER: " + w.walletId() + " (type=" + w.type() + ")");
        }
    }
}
