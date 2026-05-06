package com.intuit.walletservice.businesslogic.workflow;

import com.intuit.walletservice.businesslogic.activity.ExecuteTransferActivity;
import com.intuit.walletservice.businesslogic.activity.ValidateFundActivity;
import com.intuit.walletservice.businesslogic.core.ExecuteTransferRequest;
import com.intuit.walletservice.businesslogic.core.ExecuteTransferResult;
import com.intuit.walletservice.businesslogic.core.SystemWallets;
import com.intuit.walletservice.businesslogic.core.TransactionView;
import com.intuit.walletservice.businesslogic.core.WalletNotFoundException;
import com.intuit.walletservice.businesslogic.workflow.FundWalletWorkflow.FundWalletInput;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

class FundWalletWorkflowTest {

    private TestWorkflowEnvironment testEnv;
    private ValidateFundActivity validateFundActivity;
    private ExecuteTransferActivity executeTransferActivity;

    @BeforeEach
    void setup() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(FundWalletWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(FundWalletWorkflowImpl.class);

        validateFundActivity = mock(ValidateFundActivity.class);
        executeTransferActivity = mock(ExecuteTransferActivity.class);
        worker.registerActivitiesImplementations(validateFundActivity, executeTransferActivity);

        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void fund_validInput_runsValidateThenExecute_passesSystemDepositsAsSender() {
        UUID target = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        TransactionView txView = new TransactionView(
                txId, "FUND", "WALLET", SystemWallets.EXTERNAL_DEPOSITS.toString(),
                "WALLET", target.toString(), "USDC",
                new BigDecimal("100.00"), BigDecimal.ZERO, "COMPLETED", now);
        when(executeTransferActivity.execute(any(ExecuteTransferRequest.class)))
                .thenReturn(new ExecuteTransferResult(txView, true));

        FundWalletWorkflow workflow = newWorkflow();
        ExecuteTransferResult actual = workflow.fund(new FundWalletInput(
                target, new BigDecimal("100.00"), "USDC", "fund-k1", "hash"));

        assertThat(actual.created()).isTrue();
        verify(validateFundActivity).validateFund(target);
        ArgumentCaptor<ExecuteTransferRequest> captor = ArgumentCaptor.forClass(ExecuteTransferRequest.class);
        verify(executeTransferActivity).execute(captor.capture());
        assertThat(captor.getValue().txType()).isEqualTo("FUND");
        assertThat(captor.getValue().fromWalletId()).isEqualTo(SystemWallets.EXTERNAL_DEPOSITS);
        assertThat(captor.getValue().toWalletId()).isEqualTo(target);
    }

    @Test
    void fund_unknownTarget_skipsExecute() {
        UUID target = UUID.randomUUID();
        doThrow(new WalletNotFoundException("Wallet not found: " + target))
                .when(validateFundActivity).validateFund(target);

        FundWalletWorkflow workflow = newWorkflow();
        assertThatThrownBy(() -> workflow.fund(new FundWalletInput(
                target, new BigDecimal("10.00"), "USDC", "k1", "hash")))
                .isInstanceOf(WorkflowFailedException.class);

        verify(executeTransferActivity, never()).execute(any());
    }

    private FundWalletWorkflow newWorkflow() {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(FundWalletWorkflow.TASK_QUEUE)
                .setWorkflowId("test-fund-" + UUID.randomUUID())
                .build();
        return testEnv.getWorkflowClient().newWorkflowStub(FundWalletWorkflow.class, options);
    }
}
