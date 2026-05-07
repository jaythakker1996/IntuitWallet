package com.intuit.walletservice.businesslogic.activity;

import com.intuit.walletservice.businesslogic.core.ExecuteTransferRequest;
import com.intuit.walletservice.businesslogic.core.ExecuteTransferResult;
import com.intuit.walletservice.businesslogic.core.LedgerCoreService;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(workers = "wallet-service-worker")
public class ExecuteTransferActivityImpl implements ExecuteTransferActivity {

    private final LedgerCoreService ledgerCoreService;

    public ExecuteTransferActivityImpl(LedgerCoreService ledgerCoreService) {
        this.ledgerCoreService = ledgerCoreService;
    }

    @Override
    public ExecuteTransferResult execute(ExecuteTransferRequest request) {
        return ledgerCoreService.executeTransfer(request);
    }
}
