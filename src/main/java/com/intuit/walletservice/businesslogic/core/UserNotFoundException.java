package com.intuit.walletservice.businesslogic.core;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID intuitAccountId) {
        super("User not found: " + intuitAccountId);
    }
}
