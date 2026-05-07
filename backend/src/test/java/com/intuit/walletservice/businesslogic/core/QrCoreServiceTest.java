package com.intuit.walletservice.businesslogic.core;

import com.intuit.walletservice.dal.entity.Qr;
import com.intuit.walletservice.dal.entity.Wallet;
import com.intuit.walletservice.dal.repository.QrRepository;
import com.intuit.walletservice.dal.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(QrCoreService.class)
@ActiveProfiles("test")
class QrCoreServiceTest {

    @Autowired
    private QrCoreService qrCoreService;

    @Autowired
    private QrRepository qrRepository;

    @Autowired
    private WalletRepository walletRepository;

    private UUID activeWalletId;
    private UUID frozenWalletId;

    @BeforeEach
    void seedWallets() {
        activeWalletId = UUID.randomUUID();
        frozenWalletId = UUID.randomUUID();
        walletRepository.save(new Wallet(activeWalletId, UUID.randomUUID(), "USER", "ACTIVE"));
        walletRepository.save(new Wallet(frozenWalletId, UUID.randomUUID(), "USER", "FROZEN"));
    }

    @Test
    void createIfMissing_newWallet_persistsRowAndReturnsCreatedTrue() {
        QrCoreService.CreateQrResult result = qrCoreService.createIfMissing(activeWalletId);

        assertThat(result.created()).isTrue();
        assertThat(result.qr().qrCodeId()).isNotNull();
        assertThat(result.qr().walletId()).isEqualTo(activeWalletId);
        assertThat(result.qr().payload()).isEqualTo("wallet:" + activeWalletId);
        assertThat(result.qr().type()).isEqualTo("STATIC");
        assertThat(result.qr().status()).isEqualTo("ACTIVE");
        assertThat(result.qr().expiresAt()).isNull();
        assertThat(qrRepository.count()).isEqualTo(1);
    }

    @Test
    void createIfMissing_existingQr_returnsExistingAndCreatedFalse() {
        QrCoreService.CreateQrResult first = qrCoreService.createIfMissing(activeWalletId);
        QrCoreService.CreateQrResult second = qrCoreService.createIfMissing(activeWalletId);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.qr().qrCodeId()).isEqualTo(first.qr().qrCodeId());
        assertThat(qrRepository.count()).isEqualTo(1);
    }

    @Test
    void createIfMissing_payloadIsExpectedFormat() {
        QrCoreService.CreateQrResult result = qrCoreService.createIfMissing(activeWalletId);
        assertThat(result.qr().payload()).isEqualTo("wallet:" + activeWalletId);
    }

    @Test
    void createIfMissing_unknownWallet_throwsWalletNotFound() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> qrCoreService.createIfMissing(unknown))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void createIfMissing_frozenWallet_throwsWalletNotActive() {
        assertThatThrownBy(() -> qrCoreService.createIfMissing(frozenWalletId))
                .isInstanceOf(WalletNotActiveException.class)
                .hasMessageContaining(frozenWalletId.toString())
                .hasMessageContaining("FROZEN");
    }

    @Test
    void createIfMissing_concurrentInsertResolvedToWinner() {
        // Pre-insert a competing QR row directly through the repository.
        UUID preExistingQrId = UUID.randomUUID();
        qrRepository.save(new Qr(preExistingQrId, activeWalletId,
                "wallet:" + activeWalletId, "STATIC", "ACTIVE"));

        QrCoreService.CreateQrResult result = qrCoreService.createIfMissing(activeWalletId);

        assertThat(result.created()).isFalse();
        assertThat(result.qr().qrCodeId()).isEqualTo(preExistingQrId);
        assertThat(qrRepository.count()).isEqualTo(1);
    }

    @Test
    void getByWalletId_existing_returnsView() {
        QrCoreService.CreateQrResult created = qrCoreService.createIfMissing(activeWalletId);
        QrView view = qrCoreService.getByWalletId(activeWalletId);

        assertThat(view.qrCodeId()).isEqualTo(created.qr().qrCodeId());
        assertThat(view.walletId()).isEqualTo(activeWalletId);
        assertThat(view.payload()).isEqualTo("wallet:" + activeWalletId);
    }

    @Test
    void getByWalletId_noQr_throwsQrNotFound() {
        // activeWalletId exists in wallets but has no QR yet.
        assertThatThrownBy(() -> qrCoreService.getByWalletId(activeWalletId))
                .isInstanceOf(QrNotFoundException.class)
                .hasMessageContaining(activeWalletId.toString());
    }
}
