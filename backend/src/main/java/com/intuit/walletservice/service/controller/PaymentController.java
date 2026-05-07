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
import com.intuit.walletservice.service.dto.FundWalletRequest;
import com.intuit.walletservice.service.dto.SendPaymentRequest;
import com.intuit.walletservice.service.dto.TransactionResponse;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@RestController
public class PaymentController {

    private final WorkflowClient workflowClient;
    private final LedgerCoreService ledgerCoreService;

    public PaymentController(WorkflowClient workflowClient, LedgerCoreService ledgerCoreService) {
        this.workflowClient = workflowClient;
        this.ledgerCoreService = ledgerCoreService;
    }

    @PostMapping("/api/v1/payments")
    public ResponseEntity<TransactionResponse> send(@Valid @RequestBody SendPaymentRequest request) {
        if (request.fromWalletId().equals(request.toWalletId())) {
            throw new IllegalArgumentException("fromWalletId and toWalletId must differ");
        }
        String fromParty = request.fromWalletId().toString();
        String requestHash = canonicalHash(
                request.fromWalletId(), request.toWalletId(), request.amount(), request.stablecoin());

        Optional<TransactionView> existing =
                ledgerCoreService.findByIdempotency(fromParty, request.idempotencyKey(), requestHash);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.OK).body(TransactionResponse.from(existing.get()));
        }

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(SendPaymentWorkflow.TASK_QUEUE)
                .setWorkflowId("send-payment-" + request.fromWalletId() + "-" + request.idempotencyKey())
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                .build();
        SendPaymentWorkflow workflow = workflowClient.newWorkflowStub(SendPaymentWorkflow.class, options);
        SendPaymentInput input = new SendPaymentInput(
                request.fromWalletId(),
                request.toWalletId(),
                request.amount(),
                request.stablecoin(),
                request.idempotencyKey(),
                requestHash);

        try {
            ExecuteTransferResult result = workflow.send(input);
            return respond(result);
        } catch (WorkflowExecutionAlreadyStarted alreadyStarted) {
            return resolveAlreadyStarted(fromParty, request.idempotencyKey(), requestHash);
        } catch (WorkflowFailedException ex) {
            throw translateWorkflowFailure(ex);
        }
    }

    @PostMapping("/api/v1/wallets/{walletId}/fund")
    public ResponseEntity<TransactionResponse> fund(
            @PathVariable UUID walletId,
            @Valid @RequestBody FundWalletRequest request) {
        UUID fromParty = SystemWallets.EXTERNAL_DEPOSITS;
        String fromPartyStr = fromParty.toString();
        String requestHash = canonicalHash(fromParty, walletId, request.amount(), request.stablecoin());

        Optional<TransactionView> existing =
                ledgerCoreService.findByIdempotency(fromPartyStr, request.idempotencyKey(), requestHash);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.OK).body(TransactionResponse.from(existing.get()));
        }

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(FundWalletWorkflow.TASK_QUEUE)
                .setWorkflowId("fund-wallet-" + walletId + "-" + request.idempotencyKey())
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                .build();
        FundWalletWorkflow workflow = workflowClient.newWorkflowStub(FundWalletWorkflow.class, options);
        FundWalletInput input = new FundWalletInput(
                walletId,
                request.amount(),
                request.stablecoin(),
                request.idempotencyKey(),
                requestHash);

        try {
            ExecuteTransferResult result = workflow.fund(input);
            return respond(result);
        } catch (WorkflowExecutionAlreadyStarted alreadyStarted) {
            return resolveAlreadyStarted(fromPartyStr, request.idempotencyKey(), requestHash);
        } catch (WorkflowFailedException ex) {
            throw translateWorkflowFailure(ex);
        }
    }

    private static ResponseEntity<TransactionResponse> respond(ExecuteTransferResult result) {
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TransactionResponse.from(result.transaction()));
    }

    private ResponseEntity<TransactionResponse> resolveAlreadyStarted(
            String fromParty, String idempotencyKey, String requestHash) {
        TransactionView existing = ledgerCoreService.findByIdempotency(fromParty, idempotencyKey, requestHash)
                .orElseThrow(() -> new IllegalStateException(
                        "Workflow already started but no matching transaction row for "
                                + fromParty + "/" + idempotencyKey));
        return ResponseEntity.status(HttpStatus.OK).body(TransactionResponse.from(existing));
    }

    private static RuntimeException translateWorkflowFailure(WorkflowFailedException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof ApplicationFailure af) {
                String type = af.getType();
                String message = af.getOriginalMessage();
                if (WalletNotFoundException.class.getName().equals(type)) {
                    return new WalletNotFoundException(message);
                }
                if (WalletNotActiveException.class.getName().equals(type)) {
                    return new WalletNotActiveException(message);
                }
                if (InsufficientBalanceException.class.getName().equals(type)) {
                    return new InsufficientBalanceException(message);
                }
                if (IdempotencyConflictException.class.getName().equals(type)) {
                    return new IdempotencyConflictException(message);
                }
                if (IllegalArgumentException.class.getName().equals(type)) {
                    return new IllegalArgumentException(message);
                }
                break;
            }
            cause = cause.getCause();
        }
        return ex;
    }

    private static String canonicalHash(UUID from, UUID to, BigDecimal amount, String stablecoin) {
        // Canonical form: pipe-separated lowercase UUIDs + stripTrailingZeros amount + uppercase stablecoin.
        // Excludes idempotencyKey by design; see spec/007 §request_hash.
        String canonical = from.toString().toLowerCase()
                + "|" + to.toString().toLowerCase()
                + "|" + amount.stripTrailingZeros().toPlainString()
                + "|" + stablecoin.toUpperCase();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unreachable) {
            throw new IllegalStateException("SHA-256 unavailable", unreachable);
        }
    }
}
