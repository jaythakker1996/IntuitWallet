package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.activity.ExecuteTransferActivity;
import com.intuit.walletservice.businesslogic.activity.ValidateTransferActivity;
import com.intuit.walletservice.businesslogic.core.ExecuteTransferRequest;
import com.intuit.walletservice.businesslogic.core.ExecuteTransferResult;
import com.intuit.walletservice.businesslogic.core.IdempotencyConflictException;
import com.intuit.walletservice.businesslogic.core.InsufficientBalanceException;
import com.intuit.walletservice.businesslogic.core.WalletNotActiveException;
import com.intuit.walletservice.businesslogic.core.WalletNotFoundException;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

@WorkflowImpl(workers = "wallet-service-worker")
public class SendPaymentWorkflowImpl implements SendPaymentWorkflow {

    private static final String TX_TYPE_SEND = "SEND";

    private final ValidateTransferActivity validateTransferActivity = Workflow.newActivityStub(
            ValidateTransferActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setDoNotRetry(
                                    WalletNotFoundException.class.getName(),
                                    WalletNotActiveException.class.getName(),
                                    IllegalArgumentException.class.getName())
                            .build())
                    .build());

    private final ExecuteTransferActivity executeTransferActivity = Workflow.newActivityStub(
            ExecuteTransferActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(5)
                            .setDoNotRetry(
                                    IdempotencyConflictException.class.getName(),
                                    InsufficientBalanceException.class.getName(),
                                    WalletNotFoundException.class.getName(),
                                    WalletNotActiveException.class.getName())
                            .build())
                    .build());

    @Override
    public ExecuteTransferResult send(SendPaymentInput input) {
        validateTransferActivity.validateTransfer(input.fromWalletId(), input.toWalletId());
        ExecuteTransferRequest req = new ExecuteTransferRequest(
                TX_TYPE_SEND,
                input.fromWalletId(),
                input.toWalletId(),
                input.amount(),
                input.stablecoin(),
                input.idempotencyKey(),
                input.requestHash());
        return executeTransferActivity.execute(req);
    }
}
