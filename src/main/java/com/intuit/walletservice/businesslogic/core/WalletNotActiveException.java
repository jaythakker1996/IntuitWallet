package com.intuit.walletservice.businesslogic.core;

public class WalletNotActiveException extends RuntimeException {

    public WalletNotActiveException(String message) {
        super(message);
    }
}
