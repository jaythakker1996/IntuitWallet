package com.intuit.walletservice.dal.repository;

import com.intuit.walletservice.dal.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByIntuitAccountId(UUID intuitAccountId);
}
