package com.intuit.walletservice.businesslogic.activity;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface PingActivities {

    String recordPing(String message);
}
