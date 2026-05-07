package com.intuit.walletservice.businesslogic.core;

import com.intuit.walletservice.dal.entity.Qr;
import com.intuit.walletservice.dal.entity.Wallet;
import com.intuit.walletservice.dal.repository.QrRepository;
import com.intuit.walletservice.dal.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class QrCoreService {

    static final String TYPE_STATIC = "STATIC";
    static final String STATUS_ACTIVE = "ACTIVE";
    static final String WALLET_STATUS_ACTIVE = "ACTIVE";

    private final QrRepository qrRepository;
    private final WalletRepository walletRepository;

    public QrCoreService(QrRepository qrRepository, WalletRepository walletRepository) {
        this.qrRepository = qrRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional
    public CreateQrResult createIfMissing(UUID walletId) {
        Optional<Qr> existing = qrRepository.findByWalletId(walletId);
        if (existing.isPresent()) {
            return new CreateQrResult(toView(existing.get()), false);
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        if (!WALLET_STATUS_ACTIVE.equals(wallet.getStatus())) {
            throw new WalletNotActiveException(
                    "Wallet not ACTIVE: " + walletId + " (status=" + wallet.getStatus() + ")");
        }

        try {
            Qr saved = qrRepository.save(new Qr(
                    UUID.randomUUID(), walletId, buildPayload(walletId), TYPE_STATIC, STATUS_ACTIVE));
            return new CreateQrResult(toView(saved), true);
        } catch (DataIntegrityViolationException race) {
            // Concurrent insert collided on UNIQUE(wallet_id); resolve to the winning row.
            Qr resolved = qrRepository.findByWalletId(walletId).orElseThrow(() -> race);
            return new CreateQrResult(toView(resolved), false);
        }
    }

    @Transactional(readOnly = true)
    public QrView getByWalletId(UUID walletId) {
        return qrRepository.findByWalletId(walletId)
                .map(QrCoreService::toView)
                .orElseThrow(() -> new QrNotFoundException(
                        "QR not found for wallet: " + walletId));
    }

    static String buildPayload(UUID walletId) {
        return "wallet:" + walletId;
    }

    private static QrView toView(Qr qr) {
        return new QrView(
                qr.getQrCodeId(),
                qr.getWalletId(),
                qr.getPayload(),
                qr.getType(),
                qr.getStatus(),
                qr.getExpiresAt(),
                qr.getCreatedAt(),
                qr.getUpdatedAt());
    }

    public record CreateQrResult(QrView qr, boolean created) {
    }
}
