package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.activity.PingActivities;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PingWorkflowTest {

    private static final String TASK_QUEUE = "wallet-service-test";

    private TestWorkflowEnvironment env;
    private Worker worker;
    private PingActivities activities;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(PingWorkflowImpl.class);
        activities = mock(PingActivities.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void pingWorkflowDelegatesToActivityAndReturnsItsResult() {
        when(activities.recordPing("hi")).thenReturn("pong");

        WorkflowClient client = env.getWorkflowClient();
        PingWorkflow workflow = client.newWorkflowStub(
                PingWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

        String result = workflow.ping("hi");

        assertThat(result).isEqualTo("pong");
        verify(activities).recordPing("hi");
    }
}
