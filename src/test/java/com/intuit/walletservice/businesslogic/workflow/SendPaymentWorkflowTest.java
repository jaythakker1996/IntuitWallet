package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.activity.ExecuteTransferActivity;
import com.intuit.walletservice.businesslogic.activity.ValidateTransferActivity;
import com.intuit.walletservice.businesslogic.core.ExecuteTransferRequest;
import com.intuit.walletservice.businesslogic.core.ExecuteTransferResult;
import com.intuit.walletservice.businesslogic.core.TransactionView;
import com.intuit.walletservice.businesslogic.core.WalletNotFoundException;
import com.intuit.walletservice.businesslogic.workflow.SendPaymentWorkflow.SendPaymentInput;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

class SendPaymentWorkflowTest {

    private TestWorkflowEnvironment testEnv;
    private ValidateTransferActivity validateTransferActivity;
    private ExecuteTransferActivity executeTransferActivity;

    @BeforeEach
    void setup() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(SendPaymentWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(SendPaymentWorkflowImpl.class);

        validateTransferActivity = mock(ValidateTransferActivity.class);
        executeTransferActivity = mock(ExecuteTransferActivity.class);
        worker.registerActivitiesImplementations(validateTransferActivity, executeTransferActivity);

        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void send_validInput_runsValidateThenExecute_returnsResult() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        TransactionView txView = new TransactionView(
                txId, "SEND", "WALLET", from.toString(), "WALLET", to.toString(),
                "USDC", new BigDecimal("10.00"), BigDecimal.ZERO, "COMPLETED", now);
        when(executeTransferActivity.execute(any(ExecuteTransferRequest.class)))
                .thenReturn(new ExecuteTransferResult(txView, true));

        SendPaymentWorkflow workflow = newWorkflow();
        ExecuteTransferResult actual = workflow.send(new SendPaymentInput(
                from, to, new BigDecimal("10.00"), "USDC", "k1", "hash"));

        assertThat(actual.created()).isTrue();
        assertThat(actual.transaction().txId()).isEqualTo(txId);
        verify(validateTransferActivity).validateTransfer(from, to);
        verify(executeTransferActivity).execute(any(ExecuteTransferRequest.class));
    }

    @Test
    void send_unknownWallet_skipsExecute() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        doThrow(new WalletNotFoundException("Wallet not found: " + from))
                .when(validateTransferActivity).validateTransfer(from, to);

        SendPaymentWorkflow workflow = newWorkflow();
        assertThatThrownBy(() -> workflow.send(new SendPaymentInput(
                from, to, new BigDecimal("10.00"), "USDC", "k1", "hash")))
                .isInstanceOf(WorkflowFailedException.class);

        verify(executeTransferActivity, never()).execute(any());
    }

    private SendPaymentWorkflow newWorkflow() {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(SendPaymentWorkflow.TASK_QUEUE)
                .setWorkflowId("test-send-" + UUID.randomUUID())
                .build();
        return testEnv.getWorkflowClient().newWorkflowStub(SendPaymentWorkflow.class, options);
    }
}
