package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.core.ExecuteTransferResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;
import java.util.UUID;

@WorkflowInterface
public interface FundWalletWorkflow {

    String TASK_QUEUE = "wallet-service";

    @WorkflowMethod
    ExecuteTransferResult fund(FundWalletInput input);

    record FundWalletInput(
            UUID targetWalletId,
            BigDecimal amount,
            String stablecoin,
            String idempotencyKey,
            String requestHash) {
    }
}
