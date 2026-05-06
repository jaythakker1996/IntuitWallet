package com.intuit.walletservice.businesslogic.core;

import com.intuit.walletservice.dal.entity.PingLog;
import com.intuit.walletservice.dal.repository.PingLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PingCoreService {

    private final PingLogRepository pingLogRepository;

    public PingCoreService(PingLogRepository pingLogRepository) {
        this.pingLogRepository = pingLogRepository;
    }

    @Transactional
    public String recordPing(String message) {
        pingLogRepository.save(new PingLog(message));
        return "pong";
    }
}
