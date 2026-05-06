package com.intuit.walletservice.businesslogic.core;

import com.fasterxml.uuid.Generators;
import com.intuit.walletservice.dal.entity.LedgerEntry;
import com.intuit.walletservice.dal.entity.Transaction;
import com.intuit.walletservice.dal.entity.Wallet;
import com.intuit.walletservice.dal.repository.LedgerEntryRepository;
import com.intuit.walletservice.dal.repository.TransactionRepository;
import com.intuit.walletservice.dal.repository.WalletRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LedgerCoreService {

    static final String WALLET_TYPE_USER = "USER";
    static final String WALLET_TYPE_SYSTEM = "SYSTEM";
    static final String WALLET_STATUS_ACTIVE = "ACTIVE";

    static final String FROM_TYPE_WALLET = "WALLET";
    static final String TO_TYPE_WALLET = "WALLET";

    static final String STATUS_COMPLETED = "COMPLETED";
    static final String ENTRY_TYPE_DEBIT = "DEBIT";
    static final String ENTRY_TYPE_CREDIT = "CREDIT";

    static final String DIRECTION_OUTBOUND = "OUTBOUND";
    static final String DIRECTION_INBOUND = "INBOUND";

    static final int TRANSACTION_LIST_LIMIT = 100;

    static final String CONSTRAINT_TX_IDEMPOTENCY = "idx_tx_idempotency";
    static final String CONSTRAINT_LEDGER_SEQ = "idx_ledger_seq";

    private static final BigDecimal ZERO = new BigDecimal("0");

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;

    public LedgerCoreService(
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            WalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public WalletBalanceView getBalance(UUID walletId, String stablecoin) {
        return ledgerEntryRepository
                .findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(walletId, stablecoin)
                .map(LedgerCoreService::toBalanceView)
                .orElseThrow(() -> new StablecoinBalanceNotFoundException(
                        "Stablecoin " + stablecoin + " has not been used by wallet " + walletId));
    }

    @Transactional(readOnly = true)
    public List<WalletBalanceView> getBalances(UUID walletId) {
        return ledgerEntryRepository.findLatestEntriesForWallet(walletId).stream()
                .map(LedgerCoreService::toBalanceView)
                .toList();
    }

    private static WalletBalanceView toBalanceView(LedgerEntry entry) {
        return new WalletBalanceView(
                entry.getWalletId(),
                entry.getStablecoin(),
                entry.getRunningAvailable(),
                entry.getRunningPending(),
                entry.getEntrySequence(),
                entry.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionView> getTransactionsForWallet(UUID walletId) {
        List<Transaction> txs = transactionRepository.findForWallet(
                walletId.toString(), PageRequest.of(0, TRANSACTION_LIST_LIMIT));
        if (txs.isEmpty()) {
            return List.of();
        }
        List<UUID> txIds = txs.stream().map(Transaction::getTxId).toList();
        Map<UUID, LedgerEntry> entriesByTx = ledgerEntryRepository
                .findByWalletIdAndTxIdIn(walletId, txIds)
                .stream()
                .collect(Collectors.toMap(LedgerEntry::getTxId, Function.identity()));
        return txs.stream()
                .map(tx -> toWalletTransactionView(tx, walletId, entriesByTx.get(tx.getTxId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public WalletTransactionView getTransactionForWallet(UUID walletId, UUID txId) {
        Transaction tx = transactionRepository.findByTxIdAndWallet(txId, walletId.toString())
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found for wallet " + walletId + ": " + txId));
        LedgerEntry entry = ledgerEntryRepository.findByTxIdAndWalletId(txId, walletId)
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger entry missing for tx " + txId + " on wallet " + walletId));
        return toWalletTransactionView(tx, walletId, entry);
    }

    private static WalletTransactionView toWalletTransactionView(
            Transaction tx, UUID walletId, LedgerEntry entry) {
        if (entry == null) {
            throw new IllegalStateException(
                    "Ledger entry missing for tx " + tx.getTxId() + " on wallet " + walletId);
        }
        boolean outbound = tx.getFromParty().equals(walletId.toString());
        String direction = outbound ? DIRECTION_OUTBOUND : DIRECTION_INBOUND;
        return new WalletTransactionView(
                tx.getTxId(),
                tx.getType(),
                direction,
                entry.getEntryType(),
                UUID.fromString(tx.getFromParty()),
                UUID.fromString(tx.getToParty()),
                tx.getAmount(),
                tx.getStablecoin(),
                tx.getFee(),
                tx.getStatus(),
                entry.getRunningAvailable(),
                entry.getRunningPending(),
                entry.getEntrySequence(),
                tx.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Optional<TransactionView> findByIdempotency(
            String fromParty, String idempotencyKey, String expectedRequestHash) {
        return transactionRepository.findByFromPartyAndIdempotencyKey(fromParty, idempotencyKey)
                .map(tx -> {
                    if (!tx.getRequestHash().equals(expectedRequestHash)) {
                        throw new IdempotencyConflictException(
                                "Idempotency key reused with a different request body: " + idempotencyKey);
                    }
                    return toView(tx);
                });
    }

    @Transactional
    public ExecuteTransferResult executeTransfer(ExecuteTransferRequest req) {
        String fromParty = req.fromWalletId().toString();

        // 1. Idempotency lookup.
        Optional<Transaction> existing =
                transactionRepository.findByFromPartyAndIdempotencyKey(fromParty, req.idempotencyKey());
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            if (!tx.getRequestHash().equals(req.requestHash())) {
                throw new IdempotencyConflictException(
                        "Idempotency key reused with a different request body: " + req.idempotencyKey());
            }
            return new ExecuteTransferResult(toView(tx), false);
        }

        // 2. Load + validate both wallets.
        Wallet sender = walletRepository.findById(req.fromWalletId())
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + req.fromWalletId()));
        Wallet receiver = walletRepository.findById(req.toWalletId())
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + req.toWalletId()));
        if (!WALLET_STATUS_ACTIVE.equals(sender.getStatus())) {
            throw new WalletNotActiveException(
                    "Sender wallet not ACTIVE: " + sender.getWalletId() + " (status=" + sender.getStatus() + ")");
        }
        if (!WALLET_STATUS_ACTIVE.equals(receiver.getStatus())) {
            throw new WalletNotActiveException(
                    "Receiver wallet not ACTIVE: " + receiver.getWalletId() + " (status=" + receiver.getStatus() + ")");
        }

        // 3. Read latest entries for both sides; treat null as zero.
        Optional<LedgerEntry> senderLatestOpt =
                ledgerEntryRepository.findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(
                        req.fromWalletId(), req.stablecoin());
        Optional<LedgerEntry> receiverLatestOpt =
                ledgerEntryRepository.findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(
                        req.toWalletId(), req.stablecoin());

        BigDecimal senderAvailBefore = senderLatestOpt.map(LedgerEntry::getRunningAvailable).orElse(ZERO);
        BigDecimal senderPendingBefore = senderLatestOpt.map(LedgerEntry::getRunningPending).orElse(ZERO);
        long senderSeqBefore = senderLatestOpt.map(LedgerEntry::getEntrySequence).orElse(0L);

        BigDecimal receiverAvailBefore = receiverLatestOpt.map(LedgerEntry::getRunningAvailable).orElse(ZERO);
        BigDecimal receiverPendingBefore = receiverLatestOpt.map(LedgerEntry::getRunningPending).orElse(ZERO);
        long receiverSeqBefore = receiverLatestOpt.map(LedgerEntry::getEntrySequence).orElse(0L);

        // 4. Balance check (USER senders only; SYSTEM senders may run negative).
        if (WALLET_TYPE_USER.equals(sender.getType())
                && senderAvailBefore.compareTo(req.amount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance for wallet " + sender.getWalletId()
                            + " stablecoin=" + req.stablecoin()
                            + " available=" + senderAvailBefore + " requested=" + req.amount());
        }

        // 5. Build new running balances.
        BigDecimal senderAvailAfter = senderAvailBefore.subtract(req.amount());
        BigDecimal receiverAvailAfter = receiverAvailBefore.add(req.amount());

        // 6. Insert tx + 2 ledger entries in this same @Transactional.
        UUID txId = uuidv7();
        Transaction tx = new Transaction(
                txId,
                req.txType(),
                FROM_TYPE_WALLET,
                fromParty,
                TO_TYPE_WALLET,
                req.toWalletId().toString(),
                req.stablecoin(),
                req.amount(),
                ZERO,
                STATUS_COMPLETED,
                req.idempotencyKey(),
                req.requestHash());

        LedgerEntry senderDebit = new LedgerEntry(
                uuidv7(),
                txId,
                req.fromWalletId(),
                req.stablecoin(),
                ENTRY_TYPE_DEBIT,
                req.amount(),
                senderAvailAfter,
                senderPendingBefore,
                senderSeqBefore + 1);

        LedgerEntry receiverCredit = new LedgerEntry(
                uuidv7(),
                txId,
                req.toWalletId(),
                req.stablecoin(),
                ENTRY_TYPE_CREDIT,
                req.amount(),
                receiverAvailAfter,
                receiverPendingBefore,
                receiverSeqBefore + 1);

        try {
            transactionRepository.saveAndFlush(tx);
            ledgerEntryRepository.saveAndFlush(senderDebit);
            ledgerEntryRepository.saveAndFlush(receiverCredit);
        } catch (DataIntegrityViolationException ex) {
            String constraint = extractConstraintName(ex);
            if (CONSTRAINT_TX_IDEMPOTENCY.equals(constraint)) {
                // Concurrent insert raced and won. Resolve to the winning row.
                Transaction winning = transactionRepository
                        .findByFromPartyAndIdempotencyKey(fromParty, req.idempotencyKey())
                        .orElseThrow(() -> ex);
                if (!winning.getRequestHash().equals(req.requestHash())) {
                    throw new IdempotencyConflictException(
                            "Idempotency key reused with a different request body: " + req.idempotencyKey());
                }
                return new ExecuteTransferResult(toView(winning), false);
            }
            // idx_ledger_seq collision (or any other constraint) — rethrow so Temporal retries.
            throw ex;
        }

        return new ExecuteTransferResult(toView(tx), true);
    }

    private static UUID uuidv7() {
        return Generators.timeBasedEpochGenerator().generate();
    }

    private static String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null) {
                    // Hibernate sometimes prefixes with schema name (e.g. "public.idx_tx_idempotency").
                    int dot = name.lastIndexOf('.');
                    return dot >= 0 ? name.substring(dot + 1) : name;
                }
                break;
            }
            cause = cause.getCause();
        }
        return null;
    }

    static TransactionView toView(Transaction tx) {
        return new TransactionView(
                tx.getTxId(),
                tx.getType(),
                tx.getFromType(),
                tx.getFromParty(),
                tx.getToType(),
                tx.getToParty(),
                tx.getStablecoin(),
                tx.getAmount(),
                tx.getFee(),
                tx.getStatus(),
                tx.getCreatedAt());
    }
}
