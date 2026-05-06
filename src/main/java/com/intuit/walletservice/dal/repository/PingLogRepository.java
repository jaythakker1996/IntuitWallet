package com.intuit.walletservice.dal.repository;

import com.intuit.walletservice.dal.entity.PingLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PingLogRepository extends JpaRepository<PingLog, Long> {
}
