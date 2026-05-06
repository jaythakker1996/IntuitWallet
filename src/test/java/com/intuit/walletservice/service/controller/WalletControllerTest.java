package com.intuit.walletservice.service.controller;

import com.intuit.walletservice.businesslogic.core.LedgerCoreService;
import com.intuit.walletservice.businesslogic.core.StablecoinBalanceNotFoundException;
import com.intuit.walletservice.businesslogic.core.UserNotFoundException;
import com.intuit.walletservice.businesslogic.core.WalletBalanceView;
import com.intuit.walletservice.businesslogic.core.WalletCoreService;
import com.intuit.walletservice.businesslogic.core.WalletCoreService.CreateWalletResult;
import com.intuit.walletservice.businesslogic.core.WalletNotFoundException;
import com.intuit.walletservice.businesslogic.core.WalletView;
import com.intuit.walletservice.businesslogic.workflow.CreateWalletWorkflow;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@Import(ApiExceptionHandler.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowClient workflowClient;

    @MockBean
    private WalletCoreService walletCoreService;

    @MockBean
    private LedgerCoreService ledgerCoreService;

    @Test
    void post_validBodyNewUser_returns201() throws Exception {
        UUID intuitAccountId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        WalletView view = new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now);
        CreateWalletWorkflow stub = mockWorkflowStub();
        when(stub.createWallet(eq(intuitAccountId))).thenReturn(new CreateWalletResult(view, true));

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intuitAccountId\":\"" + intuitAccountId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.intuitAccountId").value(intuitAccountId.toString()))
                .andExpect(jsonPath("$.type").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void post_validBodyExisting_returns200() throws Exception {
        UUID intuitAccountId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        WalletView view = new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now);
        CreateWalletWorkflow stub = mockWorkflowStub();
        when(stub.createWallet(eq(intuitAccountId))).thenReturn(new CreateWalletResult(view, false));

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intuitAccountId\":\"" + intuitAccountId + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void post_unknownUser_returns404() throws Exception {
        UUID intuitAccountId = UUID.randomUUID();
        CreateWalletWorkflow stub = mockWorkflowStub();
        ApplicationFailure userNotFound = ApplicationFailure.newFailure(
                "User not found: " + intuitAccountId,
                UserNotFoundException.class.getName());
        ActivityFailure activityFailure = mock(ActivityFailure.class);
        when(activityFailure.getCause()).thenReturn(userNotFound);
        WorkflowFailedException wfx = mock(WorkflowFailedException.class);
        when(wfx.getCause()).thenReturn(activityFailure);
        when(stub.createWallet(eq(intuitAccountId))).thenThrow(wfx);

        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intuitAccountId\":\"" + intuitAccountId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_blankBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_malformedUuid_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intuitAccountId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_existing_returns200() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID intuitAccountId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        WalletView view = new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now);
        when(walletCoreService.getById(eq(walletId))).thenReturn(view);

        mockMvc.perform(get("/api/v1/wallets/{walletId}", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.intuitAccountId").value(intuitAccountId.toString()));
    }

    @Test
    void getById_unknown_returns404() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(walletCoreService.getById(eq(walletId)))
                .thenThrow(new WalletNotFoundException("Wallet not found: " + walletId));

        mockMvc.perform(get("/api/v1/wallets/{walletId}", walletId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIntuitAccountId_existing_returns200() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID intuitAccountId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        WalletView view = new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now);
        when(walletCoreService.getByIntuitAccountId(eq(intuitAccountId))).thenReturn(view);

        mockMvc.perform(get("/api/v1/wallets")
                        .param("intuitAccountId", intuitAccountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.intuitAccountId").value(intuitAccountId.toString()));
    }

    @Test
    void getByIntuitAccountId_unknown_returns404() throws Exception {
        UUID intuitAccountId = UUID.randomUUID();
        when(walletCoreService.getByIntuitAccountId(eq(intuitAccountId)))
                .thenThrow(new WalletNotFoundException(
                        "Wallet not found for intuitAccountId: " + intuitAccountId));

        mockMvc.perform(get("/api/v1/wallets")
                        .param("intuitAccountId", intuitAccountId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIntuitAccountId_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/wallets"))
                .andExpect(status().isBadRequest());
    }

    // ===== balances (spec/008) =====

    @Test
    void getBalances_existing_returns200WithList() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID intuitAccountId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        when(walletCoreService.getById(eq(walletId)))
                .thenReturn(new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now));
        when(ledgerCoreService.getBalances(eq(walletId))).thenReturn(List.of(
                new WalletBalanceView(walletId, "EURC", new BigDecimal("25.00"), BigDecimal.ZERO, 1L, now),
                new WalletBalanceView(walletId, "USDC", new BigDecimal("100.00"), BigDecimal.ZERO, 5L, now)));

        mockMvc.perform(get("/api/v1/wallets/{id}/balances", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.balances.length()").value(2))
                .andExpect(jsonPath("$.balances[0].stablecoin").value("EURC"))
                .andExpect(jsonPath("$.balances[0].runningAvailable").value(25.00))
                .andExpect(jsonPath("$.balances[1].stablecoin").value("USDC"))
                .andExpect(jsonPath("$.balances[1].runningAvailable").value(100.00))
                .andExpect(jsonPath("$.balances[1].lastEntrySequence").value(5));
    }

    @Test
    void getBalances_existingNoEntries_returns200WithEmptyList() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID intuitAccountId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        when(walletCoreService.getById(eq(walletId)))
                .thenReturn(new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now));
        when(ledgerCoreService.getBalances(eq(walletId))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/wallets/{id}/balances", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.balances.length()").value(0));
    }

    @Test
    void getBalances_unknownWallet_returns404() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(walletCoreService.getById(eq(walletId)))
                .thenThrow(new WalletNotFoundException("Wallet not found: " + walletId));

        mockMvc.perform(get("/api/v1/wallets/{id}/balances", walletId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"));
    }

    @Test
    void getBalances_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/not-a-uuid/balances"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBalance_existingWithEntries_returns200WithLatest() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID intuitAccountId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        when(walletCoreService.getById(eq(walletId)))
                .thenReturn(new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now));
        when(ledgerCoreService.getBalance(eq(walletId), eq("USDC"))).thenReturn(
                new WalletBalanceView(walletId, "USDC", new BigDecimal("100.00"), BigDecimal.ZERO, 5L, now));

        mockMvc.perform(get("/api/v1/wallets/{id}/balances/{coin}", walletId, "USDC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.stablecoin").value("USDC"))
                .andExpect(jsonPath("$.runningAvailable").value(100.00))
                .andExpect(jsonPath("$.lastEntrySequence").value(5));
    }

    @Test
    void getBalance_existingNoStablecoinEntry_returns404WithStablecoinNotEnabled() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID intuitAccountId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-05-06T12:00:00Z");
        when(walletCoreService.getById(eq(walletId)))
                .thenReturn(new WalletView(walletId, intuitAccountId, "USER", "ACTIVE", now, now));
        when(ledgerCoreService.getBalance(eq(walletId), eq("EURC")))
                .thenThrow(new StablecoinBalanceNotFoundException(
                        "Stablecoin EURC has not been used by wallet " + walletId));

        mockMvc.perform(get("/api/v1/wallets/{id}/balances/{coin}", walletId, "EURC"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("STABLECOIN_NOT_ENABLED"));
    }

    @Test
    void getBalance_unknownWallet_returns404WithWalletNotFound() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(walletCoreService.getById(eq(walletId)))
                .thenThrow(new WalletNotFoundException("Wallet not found: " + walletId));

        mockMvc.perform(get("/api/v1/wallets/{id}/balances/{coin}", walletId, "USDC"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"));
    }

    @Test
    void getBalance_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/not-a-uuid/balances/USDC"))
                .andExpect(status().isBadRequest());
    }

    private CreateWalletWorkflow mockWorkflowStub() {
        CreateWalletWorkflow stub = mock(CreateWalletWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(CreateWalletWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(stub);
        return stub;
    }
}
