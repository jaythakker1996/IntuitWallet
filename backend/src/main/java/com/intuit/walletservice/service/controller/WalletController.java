package com.intuit.walletservice.service.controller;

import com.intuit.walletservice.businesslogic.core.LedgerCoreService;
import com.intuit.walletservice.businesslogic.core.UserNotFoundException;
import com.intuit.walletservice.businesslogic.core.WalletCoreService;
import com.intuit.walletservice.businesslogic.core.WalletCoreService.CreateWalletResult;
import com.intuit.walletservice.businesslogic.workflow.CreateWalletWorkflow;
import com.intuit.walletservice.service.dto.CreateWalletRequest;
import com.intuit.walletservice.service.dto.WalletBalanceResponse;
import com.intuit.walletservice.service.dto.WalletBalancesResponse;
import com.intuit.walletservice.service.dto.WalletResponse;
import com.intuit.walletservice.service.dto.WalletTransactionResponse;
import com.intuit.walletservice.service.dto.WalletTransactionsResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WorkflowClient workflowClient;
    private final WalletCoreService walletCoreService;
    private final LedgerCoreService ledgerCoreService;

    public WalletController(
            WorkflowClient workflowClient,
            WalletCoreService walletCoreService,
            LedgerCoreService ledgerCoreService) {
        this.workflowClient = workflowClient;
        this.walletCoreService = walletCoreService;
        this.ledgerCoreService = ledgerCoreService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(CreateWalletWorkflow.TASK_QUEUE)
                .setWorkflowId("create-wallet-" + request.intuitAccountId() + "-" + UUID.randomUUID())
                .build();
        CreateWalletWorkflow workflow = workflowClient.newWorkflowStub(CreateWalletWorkflow.class, options);
        try {
            CreateWalletResult result = workflow.createWallet(request.intuitAccountId());
            HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(status).body(WalletResponse.from(result.wallet()));
        } catch (WorkflowFailedException ex) {
            translateWorkflowFailure(ex, request.intuitAccountId());
            throw ex;
        }
    }

    @GetMapping("/{walletId}")
    public WalletResponse getById(@PathVariable UUID walletId) {
        return WalletResponse.from(walletCoreService.getById(walletId));
    }

    @GetMapping(params = "intuitAccountId")
    public WalletResponse getByIntuitAccountId(@RequestParam UUID intuitAccountId) {
        return WalletResponse.from(walletCoreService.getByIntuitAccountId(intuitAccountId));
    }

    @GetMapping("/{walletId}/balances")
    public WalletBalancesResponse getBalances(@PathVariable UUID walletId) {
        // Existence check first; throws WalletNotFoundException -> 404 if missing.
        walletCoreService.getById(walletId);
        return WalletBalancesResponse.from(walletId, ledgerCoreService.getBalances(walletId));
    }

    @GetMapping("/{walletId}/balances/{stablecoin}")
    public WalletBalanceResponse getBalance(
            @PathVariable UUID walletId, @PathVariable String stablecoin) {
        walletCoreService.getById(walletId);
        return WalletBalanceResponse.from(ledgerCoreService.getBalance(walletId, stablecoin));
    }

    @GetMapping("/{walletId}/transactions")
    public WalletTransactionsResponse getTransactions(@PathVariable UUID walletId) {
        walletCoreService.getById(walletId);
        return WalletTransactionsResponse.from(walletId, ledgerCoreService.getTransactionsForWallet(walletId));
    }

    @GetMapping("/{walletId}/transactions/{txId}")
    public WalletTransactionResponse getTransaction(
            @PathVariable UUID walletId, @PathVariable UUID txId) {
        walletCoreService.getById(walletId);
        return WalletTransactionResponse.from(ledgerCoreService.getTransactionForWallet(walletId, txId));
    }

    private static void translateWorkflowFailure(WorkflowFailedException ex, UUID intuitAccountId) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ApplicationFailure af
                    && UserNotFoundException.class.getName().equals(af.getType())) {
                throw new UserNotFoundException(intuitAccountId);
            }
            cause = cause.getCause();
        }
    }
}
