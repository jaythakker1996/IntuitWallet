package com.intuit.walletservice.businesslogic.activity;

import com.intuit.walletservice.businesslogic.core.ExecuteTransferRequest;
import com.intuit.walletservice.businesslogic.core.ExecuteTransferResult;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ExecuteTransferActivity {

    ExecuteTransferResult execute(ExecuteTransferRequest request);
}
