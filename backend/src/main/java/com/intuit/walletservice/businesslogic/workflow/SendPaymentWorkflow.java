package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.core.ExecuteTransferResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;
import java.util.UUID;

@WorkflowInterface
public interface SendPaymentWorkflow {

    String TASK_QUEUE = "wallet-service";

    @WorkflowMethod
    ExecuteTransferResult send(SendPaymentInput input);

    record SendPaymentInput(
            UUID fromWalletId,
            UUID toWalletId,
            BigDecimal amount,
            String stablecoin,
            String idempotencyKey,
            String requestHash) {
    }
}
