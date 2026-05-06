package com.intuit.walletservice.service.controller;

import com.intuit.walletservice.businesslogic.core.UserNotFoundException;
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

import java.time.OffsetDateTime;
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

    private CreateWalletWorkflow mockWorkflowStub() {
        CreateWalletWorkflow stub = mock(CreateWalletWorkflow.class);
        when(workflowClient.newWorkflowStub(eq(CreateWalletWorkflow.class), any(WorkflowOptions.class)))
                .thenReturn(stub);
        return stub;
    }
}
