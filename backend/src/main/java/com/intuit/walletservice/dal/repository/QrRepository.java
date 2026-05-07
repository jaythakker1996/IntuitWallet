package com.intuit.walletservice.dal.repository;

import com.intuit.walletservice.dal.entity.Qr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QrRepository extends JpaRepository<Qr, UUID> {

    Optional<Qr> findByWalletId(UUID walletId);
}
