package com.intuit.walletservice.businesslogic.activity;

import com.intuit.walletservice.businesslogic.core.UserCoreService;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ActivityImpl(workers = "wallet-service-worker")
public class ValidateUserActivityImpl implements ValidateUserActivity {

    private final UserCoreService userCoreService;

    public ValidateUserActivityImpl(UserCoreService userCoreService) {
        this.userCoreService = userCoreService;
    }

    @Override
    public void validate(UUID intuitAccountId) {
        userCoreService.getUser(intuitAccountId);
    }
}
