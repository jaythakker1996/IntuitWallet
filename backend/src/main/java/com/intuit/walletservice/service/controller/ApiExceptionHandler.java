package com.intuit.walletservice.service.controller;

import com.intuit.walletservice.businesslogic.core.IdempotencyConflictException;
import com.intuit.walletservice.businesslogic.core.InsufficientBalanceException;
import com.intuit.walletservice.businesslogic.core.QrNotFoundException;
import com.intuit.walletservice.businesslogic.core.StablecoinBalanceNotFoundException;
import com.intuit.walletservice.businesslogic.core.TransactionNotFoundException;
import com.intuit.walletservice.businesslogic.core.UserNotFoundException;
import com.intuit.walletservice.businesslogic.core.WalletNotActiveException;
import com.intuit.walletservice.businesslogic.core.WalletNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleWalletNotFound(WalletNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "WALLET_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(StablecoinBalanceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleStablecoinBalanceNotFound(StablecoinBalanceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "STABLECOIN_NOT_ENABLED", "message", ex.getMessage()));
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTransactionNotFound(TransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "TRANSACTION_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(QrNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleQrNotFound(QrNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "QR_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(WalletNotActiveException.class)
    public ResponseEntity<Map<String, String>> handleWalletNotActive(WalletNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientBalance(InsufficientBalanceException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "INSUFFICIENT_BALANCE", "message", ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, String>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "IDEMPOTENCY_CONFLICT", "message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
