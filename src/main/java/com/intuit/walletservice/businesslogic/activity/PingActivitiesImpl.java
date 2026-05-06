package com.intuit.walletservice.businesslogic.activity;

import com.intuit.walletservice.businesslogic.core.PingCoreService;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(workers = "wallet-service-worker")
public class PingActivitiesImpl implements PingActivities {

    private final PingCoreService pingCoreService;

    public PingActivitiesImpl(PingCoreService pingCoreService) {
        this.pingCoreService = pingCoreService;
    }

    @Override
    public String recordPing(String message) {
        return pingCoreService.recordPing(message);
    }
}
