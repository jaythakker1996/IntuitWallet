package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.activity.CreateWalletActivity;
import com.intuit.walletservice.businesslogic.activity.ValidateUserActivity;
import com.intuit.walletservice.businesslogic.core.UserNotFoundException;
import com.intuit.walletservice.businesslogic.core.WalletCoreService.CreateWalletResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.UUID;

@WorkflowImpl(workers = "wallet-service-worker")
public class CreateWalletWorkflowImpl implements CreateWalletWorkflow {

    private final ValidateUserActivity validateUserActivity = Workflow.newActivityStub(
            ValidateUserActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setDoNotRetry(UserNotFoundException.class.getName())
                            .build())
                    .build());

    private final CreateWalletActivity createWalletActivity = Workflow.newActivityStub(
            CreateWalletActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .build());

    @Override
    public CreateWalletResult createWallet(UUID intuitAccountId) {
        validateUserActivity.validate(intuitAccountId);
        return createWalletActivity.createIfMissing(intuitAccountId);
    }
}
