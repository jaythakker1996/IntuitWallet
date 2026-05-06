package com.intuit.walletservice.businesslogic.core;

public record ExecuteTransferResult(TransactionView transaction, boolean created) {
}
