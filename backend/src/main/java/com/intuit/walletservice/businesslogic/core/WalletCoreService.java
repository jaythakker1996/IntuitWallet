package com.intuit.walletservice.businesslogic.core;

import com.intuit.walletservice.dal.entity.Wallet;
import com.intuit.walletservice.dal.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class WalletCoreService {

    static final String TYPE_USER = "USER";
    static final String STATUS_ACTIVE = "ACTIVE";

    private final WalletRepository walletRepository;

    public WalletCoreService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public CreateWalletResult createIfMissing(UUID intuitAccountId) {
        Optional<Wallet> existing = walletRepository.findByIntuitAccountId(intuitAccountId);
        if (existing.isPresent()) {
            return new CreateWalletResult(toView(existing.get()), false);
        }
        try {
            Wallet saved = walletRepository.save(
                    new Wallet(UUID.randomUUID(), intuitAccountId, TYPE_USER, STATUS_ACTIVE));
            return new CreateWalletResult(toView(saved), true);
        } catch (DataIntegrityViolationException race) {
            // Concurrent insert collided on the unique intuit_account_id index; resolve to the winning row.
            Wallet resolved = walletRepository.findByIntuitAccountId(intuitAccountId)
                    .orElseThrow(() -> race);
            return new CreateWalletResult(toView(resolved), false);
        }
    }

    @Transactional(readOnly = true)
    public WalletView getById(UUID walletId) {
        return walletRepository.findById(walletId)
                .map(WalletCoreService::toView)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
    }

    @Transactional(readOnly = true)
    public WalletView getByIntuitAccountId(UUID intuitAccountId) {
        return walletRepository.findByIntuitAccountId(intuitAccountId)
                .map(WalletCoreService::toView)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for intuitAccountId: " + intuitAccountId));
    }

    private static WalletView toView(Wallet wallet) {
        return new WalletView(
                wallet.getWalletId(),
                wallet.getIntuitAccountId(),
                wallet.getType(),
                wallet.getStatus(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt());
    }

    public record CreateWalletResult(WalletView wallet, boolean created) {
    }
}
