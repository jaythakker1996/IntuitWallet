package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.activity.CreateWalletActivity;
import com.intuit.walletservice.businesslogic.activity.ValidateUserActivity;
import com.intuit.walletservice.businesslogic.core.UserNotFoundException;
import com.intuit.walletservice.businesslogic.core.WalletCoreService.CreateWalletResult;
import com.intuit.walletservice.businesslogic.core.WalletView;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateWalletWorkflowTest {

    private TestWorkflowEnvironment testEnv;
    private ValidateUserActivity validateUserActivity;
    private CreateWalletActivity createWalletActivity;

    @BeforeEach
    void setup() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(CreateWalletWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(CreateWalletWorkflowImpl.class);

        validateUserActivity = mock(ValidateUserActivity.class);
        createWalletActivity = mock(CreateWalletActivity.class);
        worker.registerActivitiesImplementations(validateUserActivity, createWalletActivity);

        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void createWallet_validUser_runsValidateThenCreate_returnsResult() {
        UUID intuitAccountId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        WalletView view = new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now);
        CreateWalletResult expected = new CreateWalletResult(view, true);
        when(createWalletActivity.createIfMissing(intuitAccountId)).thenReturn(expected);

        CreateWalletWorkflow workflow = newWorkflow();

        CreateWalletResult actual = workflow.createWallet(intuitAccountId);

        assertThat(actual.created()).isTrue();
        assertThat(actual.wallet().walletId()).isEqualTo(walletId);
        assertThat(actual.wallet().intuitAccountId()).isEqualTo(intuitAccountId);
        verify(validateUserActivity).validate(intuitAccountId);
        verify(createWalletActivity).createIfMissing(intuitAccountId);
    }

    @Test
    void createWallet_unknownUser_propagatesUserNotFoundAndSkipsCreate() {
        UUID intuitAccountId = UUID.randomUUID();
        doThrow(new UserNotFoundException(intuitAccountId))
                .when(validateUserActivity).validate(intuitAccountId);

        CreateWalletWorkflow workflow = newWorkflow();

        assertThatThrownBy(() -> workflow.createWallet(intuitAccountId))
                .isInstanceOf(WorkflowFailedException.class);
        verify(createWalletActivity, never()).createIfMissing(any());
    }

    private CreateWalletWorkflow newWorkflow() {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(CreateWalletWorkflow.TASK_QUEUE)
                .setWorkflowId("test-create-wallet-" + UUID.randomUUID())
                .build();
        return testEnv.getWorkflowClient().newWorkflowStub(CreateWalletWorkflow.class, options);
    }
}
