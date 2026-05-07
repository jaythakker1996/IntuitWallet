package com.intuit.walletservice.service.controller;

import com.intuit.walletservice.businesslogic.core.ExecuteTransferResult;
import com.intuit.walletservice.businesslogic.core.IdempotencyConflictException;
import com.intuit.walletservice.businesslogic.core.InsufficientBalanceException;
import com.intuit.walletservice.businesslogic.core.LedgerCoreService;
import com.intuit.walletservice.businesslogic.core.SystemWallets;
import com.intuit.walletservice.businesslogic.core.TransactionView;
import com.intuit.walletservice.businesslogic.core.WalletNotActiveException;
import com.intuit.walletservice.businesslogic.core.WalletNotFoundException;
import com.intuit.walletservice.businesslogic.workflow.FundWalletWorkflow;
import com.intuit.walletservice.businesslogic.workflow.FundWalletWorkflow.FundWalletInput;
import com.intuit.walletservice.businesslogic.workflow.SendPaymentWorkflow;
import com.intuit.walletservice.businesslogic.workflow.SendPaymentWorkflow.SendPaymentInput;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(ApiExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowClient workflowClient;

    @MockBean
    private LedgerCoreService ledgerCoreService;

    // ========== send ==========

    @Test
    void send_validBody_returns201() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        TransactionView view = newView(from, to, "SEND", "10.00");
        SendPaymentWorkflow stub = mockSendStub();
        when(stub.send(any(SendPaymentInput.class))).thenReturn(new ExecuteTransferResult(view, true));
        when(ledgerCoreService.findByIdempotency(eq(from.toString()), eq("k1"), anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"10.00","stablecoin":"USDC","idempotencyKey":"k1"}
                                """.formatted(from, to)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.txId").value(view.txId().toString()))
                .andExpect(jsonPath("$.type").value("SEND"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void send_idempotentRetryHitsPreCheck_returns200WithoutWorkflow() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        TransactionView view = newView(from, to, "SEND", "10.00");
        when(ledgerCoreService.findByIdempotency(eq(from.toString()), eq("k1"), anyString()))
                .thenReturn(Optional.of(view));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"10.00","stablecoin":"USDC","idempotencyKey":"k1"}
                                """.formatted(from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txId").value(view.txId().toString()));
    }

    @Test
    void send_idempotencyKeyDifferentBody_returns422() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(ledgerCoreService.findByIdempotency(eq(from.toString()), eq("k1"), anyString()))
                .thenThrow(new IdempotencyConflictException("Idempotency key reused: k1"));

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"10.00","stablecoin":"USDC","idempotencyKey":"k1"}
                                """.formatted(from, to)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void send_unknownWallet_returns404() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(ledgerCoreService.findByIdempotency(any(), any(), any())).thenReturn(Optional.empty());
        WorkflowFailedException wfx = workflowFailedWith(WalletNotFoundException.class, "Wallet not found: " + from);
        SendPaymentWorkflow stub = mockSendStub();
        when(stub.send(any(SendPaymentInput.class))).thenThrow(wfx);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"10.00","stablecoin":"USDC","idempotencyKey":"k1"}
                                """.formatted(from, to)))
                .andExpect(status().isNotFound());
    }

    @Test
    void send_frozenWallet_returns409() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(ledgerCoreService.findByIdempotency(any(), any(), any())).thenReturn(Optional.empty());
        WorkflowFailedException wfx = workflowFailedWith(WalletNotActiveException.class, "Sender wallet not ACTIVE");
        SendPaymentWorkflow stub = mockSendStub();
        when(stub.send(any(SendPaymentInput.class))).thenThrow(wfx);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"10.00","stablecoin":"USDC","idempotencyKey":"k1"}
                                """.formatted(from, to)))
                .andExpect(status().isConflict());
    }

    @Test
    void send_insufficientBalance_returns422() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(ledgerCoreService.findByIdempotency(any(), any(), any())).thenReturn(Optional.empty());
        WorkflowFailedException wfx = workflowFailedWith(InsufficientBalanceException.class, "Insufficient balance");
        SendPaymentWorkflow stub = mockSendStub();
        when(stub.send(any(SendPaymentInput.class))).thenThrow(wfx);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"10.00","stablecoin":"USDC","idempotencyKey":"k1"}
                                """.formatted(from, to)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    void send_sameFromAndTo_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"10.00","stablecoin":"USDC","idempotencyKey":"k1"}
                                """.formatted(id, id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_negativeAmount_returns400() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"-1.00","stablecoin":"USDC","idempotencyKey":"k1"}
                                """.formatted(from, to)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void send_missingIdempotencyKey_returns400() throws Exception {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromWalletId":"%s","toWalletId":"%s","amount":"10.00","stablecoin":"USDC"}
                                """.formatted(from, to)))
                .andExpect(status().isBadRequest());
    }

    // ========== fund ==========

    @Test
    void fund_validBody_returns201() throws Exception {
        UUID target = UUID.randomUUID();
        TransactionView view = newView(SystemWallets.EXTERNAL_DEPOSITS, target, "FUND", "100.00");
        FundWalletWorkflow stub = mockFundStub();
        when(stub.fund(any(FundWalletInput.class))).thenReturn(new ExecuteTransferResult(view, true));
        when(ledgerCoreService.findByIdempotency(eq(SystemWallets.EXTERNAL_DEPOSITS.toString()), eq("f1"), anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/wallets/{id}/fund", target)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"100.00","stablecoin":"USDC","idempotencyKey":"f1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("FUND"));
    }

    @Test
    void fund_idempotentRetry_returns200() throws Exception {
        UUID target = UUID.randomUUID();
        TransactionView view = newView(SystemWallets.EXTERNAL_DEPOSITS, target, "FUND", "100.00");
        when(ledgerCoreService.findByIdempotency(eq(SystemWallets.EXTERNAL_DEPOSITS.toString()), eq("f1"), anyString()))
                .thenReturn(Optional.of(view));

        mockMvc.perform(post("/api/v1/wallets/{id}/fund", target)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":"100.00","stablecoin":"USDC","idempotencyKey":"f1"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void fund_missingFields_returns400() throws Exception {
        UUID target = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/wallets/{id}/fund", target)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"100.00\"}"))
                .andExpect(status().isBadRequest());
    }

    // ========== helpers ==========

    private SendPaymentWorkflow mockSendStub() {
        SendPaymentWorkflow stub = mock(SendPaymentWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(SendPaymentWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(stub);
        return stub;
    }

    private FundWalletWorkflow mockFundStub() {
        FundWalletWorkflow stub = mock(FundWalletWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(FundWalletWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(stub);
        return stub;
    }

    private static WorkflowFailedException workflowFailedWith(Class<? extends RuntimeException> type, String message) {
        ApplicationFailure af = ApplicationFailure.newFailure(message, type.getName());
        ActivityFailure activityFailure = mock(ActivityFailure.class);
        when(activityFailure.getCause()).thenReturn(af);
        WorkflowFailedException wfx = mock(WorkflowFailedException.class);
        when(wfx.getCause()).thenReturn(activityFailure);
        return wfx;
    }

    private static TransactionView newView(UUID from, UUID to, String type, String amount) {
        return new TransactionView(
                UUID.randomUUID(),
                type,
                "WALLET", from.toString(),
                "WALLET", to.toString(),
                "USDC",
                new BigDecimal(amount),
                BigDecimal.ZERO,
                "COMPLETED",
                OffsetDateTime.parse("2026-05-06T12:00:00Z"));
    }
}
