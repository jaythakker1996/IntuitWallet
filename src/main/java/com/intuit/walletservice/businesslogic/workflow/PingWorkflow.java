package com.intuit.walletservice.businesslogic.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PingWorkflow {

    String TASK_QUEUE = "wallet-service";

    @WorkflowMethod
    String ping(String message);
}
