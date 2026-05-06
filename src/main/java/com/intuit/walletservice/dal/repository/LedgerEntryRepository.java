package com.intuit.walletservice.dal.repository;

import com.intuit.walletservice.dal.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Optional<LedgerEntry> findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(
            UUID walletId, String stablecoin);
}
