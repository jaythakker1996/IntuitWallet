package com.intuit.walletservice.service.controller;

import com.intuit.walletservice.businesslogic.workflow.PingWorkflow;
import com.intuit.walletservice.service.dto.PingRequest;
import com.intuit.walletservice.service.dto.PingResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ping")
public class PingController {

    private final WorkflowClient workflowClient;

    public PingController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping
    public PingResponse ping(@Valid @RequestBody PingRequest request) {
        String workflowId = "ping-" + UUID.randomUUID();
        PingWorkflow workflow = workflowClient.newWorkflowStub(
                PingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(PingWorkflow.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .build());
        String result = workflow.ping(request.message());
        return new PingResponse(result, workflowId);
    }
}
