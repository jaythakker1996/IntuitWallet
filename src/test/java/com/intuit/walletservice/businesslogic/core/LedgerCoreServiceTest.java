package com.intuit.walletservice.businesslogic.core;

import com.intuit.walletservice.dal.entity.LedgerEntry;
import com.intuit.walletservice.dal.entity.Wallet;
import com.intuit.walletservice.dal.repository.LedgerEntryRepository;
import com.intuit.walletservice.dal.repository.TransactionRepository;
import com.intuit.walletservice.dal.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(LedgerCoreService.class)
@ActiveProfiles("test")
class LedgerCoreServiceTest {

    private static final String STABLECOIN = "USDC";

    @Autowired
    private LedgerCoreService ledgerCoreService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID alice;
    private UUID bob;
    private UUID systemDeposits;

    @BeforeEach
    void seedWallets() {
        UUID aliceUserId = UUID.randomUUID();
        UUID bobUserId = UUID.randomUUID();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        walletRepository.save(new Wallet(alice, aliceUserId, "USER", "ACTIVE"));
        walletRepository.save(new Wallet(bob, bobUserId, "USER", "ACTIVE"));

        systemDeposits = SystemWallets.EXTERNAL_DEPOSITS;
        walletRepository.save(new Wallet(
                systemDeposits,
                UUID.fromString("00000000-0000-0000-0000-0000000000ed"),
                "SYSTEM",
                "ACTIVE"));
    }

    @Test
    void executeTransfer_send_happyPath_writesTxAndTwoEntries() {
        seedBalance(alice, "100.00");

        ExecuteTransferResult result = ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k1"));

        assertThat(result.created()).isTrue();
        assertThat(result.transaction().type()).isEqualTo("SEND");
        assertThat(result.transaction().amount()).isEqualByComparingTo("10.00");
        assertThat(result.transaction().status()).isEqualTo("COMPLETED");
        assertThat(latestAvail(alice)).isEqualByComparingTo("90.00");
        assertThat(latestAvail(bob)).isEqualByComparingTo("10.00");
    }

    @Test
    void executeTransfer_fund_happyPath_systemWalletGoesNegative() {
        ExecuteTransferResult result = ledgerCoreService.executeTransfer(
                req("FUND", systemDeposits, alice, "100.00", "fund-1"));

        assertThat(result.created()).isTrue();
        assertThat(latestAvail(alice)).isEqualByComparingTo("100.00");
        assertThat(latestAvail(systemDeposits)).isEqualByComparingTo("-100.00");
    }

    @Test
    void executeTransfer_fund_systemSenderSkipsBalanceCheck() {
        // System wallet starts at 0; fund anyway. Insufficient-balance check must NOT trigger.
        ExecuteTransferResult result = ledgerCoreService.executeTransfer(
                req("FUND", systemDeposits, alice, "1000000.00", "fund-big"));

        assertThat(result.created()).isTrue();
        assertThat(latestAvail(systemDeposits)).isEqualByComparingTo("-1000000.00");
    }

    @Test
    void executeTransfer_runningBalancesCarriedForward() {
        seedBalance(alice, "100.00");

        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k1"));
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "5.00", "k2"));
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "3.00", "k3"));

        assertThat(latestAvail(alice)).isEqualByComparingTo("82.00");
        assertThat(latestAvail(bob)).isEqualByComparingTo("18.00");
        assertThat(latestSeq(alice)).isEqualTo(4); // 1 initial seed + 3 sends
        assertThat(latestSeq(bob)).isEqualTo(3);
    }

    @Test
    void executeTransfer_idempotencyKeySameHash_returnsExistingNoNewRows() {
        seedBalance(alice, "100.00");
        ExecuteTransferResult first = ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k-dup"));
        long entriesAfterFirst = ledgerEntryRepository.count();
        long txAfterFirst = transactionsCount();

        ExecuteTransferResult second = ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k-dup"));

        assertThat(second.created()).isFalse();
        assertThat(second.transaction().txId()).isEqualTo(first.transaction().txId());
        assertThat(ledgerEntryRepository.count()).isEqualTo(entriesAfterFirst);
        assertThat(transactionsCount()).isEqualTo(txAfterFirst);
    }

    @Test
    void executeTransfer_idempotencyKeyDifferentHash_throwsIdempotencyConflict() {
        seedBalance(alice, "100.00");
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k-dup"));

        assertThatThrownBy(() -> ledgerCoreService.executeTransfer(req("SEND", alice, bob, "20.00", "k-dup")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void executeTransfer_insufficientBalance_throwsAndWritesNothing() {
        seedBalance(alice, "5.00");
        long txBefore = transactionsCount();
        long entriesBefore = ledgerEntryRepository.count();

        assertThatThrownBy(() -> ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k-fail")))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(transactionsCount()).isEqualTo(txBefore);
        assertThat(ledgerEntryRepository.count()).isEqualTo(entriesBefore);
    }

    @Test
    void executeTransfer_senderHasNoPriorEntries_treatsBalanceAsZero() {
        // Alice has zero entries; sending anything should fail INSUFFICIENT_BALANCE.
        assertThatThrownBy(() -> ledgerCoreService.executeTransfer(req("SEND", alice, bob, "1.00", "k-zero")))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void executeTransfer_unknownWallet_throwsWalletNotFound() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> ledgerCoreService.executeTransfer(req("SEND", alice, unknown, "1.00", "k-x")))
                .isInstanceOf(WalletNotFoundException.class);
    }

    @Test
    void findByIdempotency_match_returnsView() {
        seedBalance(alice, "100.00");
        ExecuteTransferResult result = ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k-find"));
        String hash = req("SEND", alice, bob, "10.00", "k-find").requestHash();

        assertThat(ledgerCoreService.findByIdempotency(alice.toString(), "k-find", hash))
                .isPresent()
                .get()
                .extracting(TransactionView::txId)
                .isEqualTo(result.transaction().txId());
    }

    @Test
    void findByIdempotency_mismatch_throws() {
        seedBalance(alice, "100.00");
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k-find"));

        assertThatThrownBy(() -> ledgerCoreService.findByIdempotency(alice.toString(), "k-find", "other-hash"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    // ===== getBalance / getBalances (spec/008) =====

    @Test
    void getBalance_existingEntry_returnsLatestRunningAvailable() {
        seedBalance(alice, "100.00");

        WalletBalanceView view = ledgerCoreService.getBalance(alice, STABLECOIN);

        assertThat(view.walletId()).isEqualTo(alice);
        assertThat(view.stablecoin()).isEqualTo(STABLECOIN);
        assertThat(view.runningAvailable()).isEqualByComparingTo("100.00");
        assertThat(view.runningPending()).isEqualByComparingTo("0");
        assertThat(view.lastEntrySequence()).isEqualTo(1L);
        assertThat(view.lastEntryAt()).isNotNull();
    }

    @Test
    void getBalance_noEntry_throwsStablecoinBalanceNotFound() {
        // alice exists in seedWallets but has no ledger entries.
        assertThatThrownBy(() -> ledgerCoreService.getBalance(alice, STABLECOIN))
                .isInstanceOf(StablecoinBalanceNotFoundException.class)
                .hasMessageContaining(STABLECOIN)
                .hasMessageContaining(alice.toString());
    }

    @Test
    void getBalance_drainedToZero_returnsZeroNot404() {
        seedBalance(alice, "10.00");
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k-drain"));

        WalletBalanceView view = ledgerCoreService.getBalance(alice, STABLECOIN);

        assertThat(view.runningAvailable()).isEqualByComparingTo("0");
        assertThat(view.lastEntrySequence()).isEqualTo(2L); // 1 seed + 1 send debit
    }

    @Test
    void getBalance_afterMultipleSends_reflectsLatestRunningAvailable() {
        seedBalance(alice, "100.00");
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k1"));
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "5.00", "k2"));
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "3.00", "k3"));

        assertThat(ledgerCoreService.getBalance(alice, STABLECOIN).runningAvailable())
                .isEqualByComparingTo("82.00");
    }

    @Test
    void getBalances_walletWithMultipleStablecoins_returnsOnePerStablecoinSorted() {
        seedBalanceFor(alice, "USDC", "100.00", 1L);
        seedBalanceFor(alice, "USDT", "50.00", 1L);
        seedBalanceFor(alice, "EURC", "25.00", 1L);

        List<WalletBalanceView> balances = ledgerCoreService.getBalances(alice);

        assertThat(balances).extracting(WalletBalanceView::stablecoin)
                .containsExactly("EURC", "USDC", "USDT");
        assertThat(balances).extracting(WalletBalanceView::runningAvailable)
                .extracting(BigDecimal::toPlainString)
                .containsExactly("25.00", "100.00", "50.00");
    }

    @Test
    void getBalances_walletWithNoEntries_returnsEmptyList() {
        assertThat(ledgerCoreService.getBalances(alice)).isEmpty();
    }

    @Test
    void getBalances_returnsOnlyLatestEntryPerStablecoin() {
        // Insert 3 USDC entries with increasing sequence; getBalances should pick the latest only.
        seedBalanceFor(alice, "USDC", "100.00", 1L);
        seedBalanceFor(alice, "USDC", "90.00", 2L);
        seedBalanceFor(alice, "USDC", "75.50", 3L);

        List<WalletBalanceView> balances = ledgerCoreService.getBalances(alice);

        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).runningAvailable()).isEqualByComparingTo("75.50");
        assertThat(balances.get(0).lastEntrySequence()).isEqualTo(3L);
    }

    @Test
    void getBalances_drainedStablecoin_stillAppearsWithZero() {
        seedBalance(alice, "10.00");
        ledgerCoreService.executeTransfer(req("SEND", alice, bob, "10.00", "k-drain"));

        List<WalletBalanceView> balances = ledgerCoreService.getBalances(alice);

        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).stablecoin()).isEqualTo(STABLECOIN);
        assertThat(balances.get(0).runningAvailable()).isEqualByComparingTo("0");
    }

    private void seedBalanceFor(UUID walletId, String stablecoin, String available, long sequence) {
        ledgerEntryRepository.save(new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                walletId,
                stablecoin,
                "CREDIT",
                new BigDecimal(available),
                new BigDecimal(available),
                BigDecimal.ZERO,
                sequence));
    }

    private ExecuteTransferRequest req(String type, UUID from, UUID to, String amount, String key) {
        return new ExecuteTransferRequest(
                type, from, to, new BigDecimal(amount), STABLECOIN, key, "hash-" + key + "-" + amount);
    }

    private void seedBalance(UUID walletId, String amount) {
        ledgerEntryRepository.save(new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                walletId,
                STABLECOIN,
                "CREDIT",
                new BigDecimal(amount),
                new BigDecimal(amount),
                BigDecimal.ZERO,
                1L));
    }

    private BigDecimal latestAvail(UUID walletId) {
        return ledgerEntryRepository.findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(walletId, STABLECOIN)
                .map(LedgerEntry::getRunningAvailable)
                .orElse(BigDecimal.ZERO);
    }

    private long latestSeq(UUID walletId) {
        return ledgerEntryRepository.findTopByWalletIdAndStablecoinOrderByEntrySequenceDesc(walletId, STABLECOIN)
                .map(LedgerEntry::getEntrySequence)
                .orElse(0L);
    }

    private long transactionsCount() {
        return transactionRepository.count();
    }
}
