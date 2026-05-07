package com.intuit.walletservice.businesslogic.core;

public class QrNotFoundException extends RuntimeException {

    public QrNotFoundException(String message) {
        super(message);
    }
}
