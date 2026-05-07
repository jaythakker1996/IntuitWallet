package com.intuit.walletservice.dal.repository;

import com.intuit.walletservice.dal.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Optional<LedgerEntry> findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(
            UUID walletId, String stablecoin);

    @Query("""
            SELECT le FROM LedgerEntry le
            WHERE le.walletId = :walletId
              AND le.entrySequence = (
                SELECT MAX(le2.entrySequence) FROM LedgerEntry le2
                WHERE le2.walletId = le.walletId AND le2.stablecoin = le.stablecoin)
            ORDER BY le.stablecoin
            """)
    List<LedgerEntry> findLatestEntriesForWallet(@Param("walletId") UUID walletId);

    Optional<LedgerEntry> findByTxIdAndWalletId(UUID txId, UUID walletId);

    List<LedgerEntry> findByWalletIdAndTxIdIn(UUID walletId, Collection<UUID> txIds);
}
