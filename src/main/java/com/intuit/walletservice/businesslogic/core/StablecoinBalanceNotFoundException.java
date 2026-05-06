package com.intuit.walletservice.businesslogic.core;

public class StablecoinBalanceNotFoundException extends RuntimeException {

    public StablecoinBalanceNotFoundException(String message) {
        super(message);
    }
}
