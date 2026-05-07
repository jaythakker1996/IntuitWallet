package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.core.WalletCoreService.CreateWalletResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.UUID;

@WorkflowInterface
public interface CreateWalletWorkflow {

    String TASK_QUEUE = "wallet-service";

    @WorkflowMethod
    CreateWalletResult createWallet(UUID intuitAccountId);
}
