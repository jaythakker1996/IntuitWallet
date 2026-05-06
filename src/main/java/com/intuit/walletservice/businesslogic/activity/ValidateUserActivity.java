package com.intuit.walletservice.businesslogic.activity;

import io.temporal.activity.ActivityInterface;

import java.util.UUID;

@ActivityInterface
public interface ValidateUserActivity {

    void validate(UUID intuitAccountId);
}
