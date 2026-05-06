package com.intuit.walletservice.dal.repository;

import com.intuit.walletservice.dal.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByFromPartyAndIdempotencyKey(String fromParty, String idempotencyKey);
}
