package com.intuit.walletservice.dal.repository;

import com.intuit.walletservice.dal.entity.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByFromPartyAndIdempotencyKey(String fromParty, String idempotencyKey);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.fromParty = :walletId OR t.toParty = :walletId
            ORDER BY t.createdAt DESC
            """)
    List<Transaction> findForWallet(@Param("walletId") String walletId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.txId = :txId
              AND (t.fromParty = :walletId OR t.toParty = :walletId)
            """)
    Optional<Transaction> findByTxIdAndWallet(
            @Param("txId") UUID txId, @Param("walletId") String walletId);
}
