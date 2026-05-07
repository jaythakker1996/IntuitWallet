package com.intuit.walletservice.businesslogic.core;

import com.intuit.walletservice.dal.entity.Wallet;
import com.intuit.walletservice.dal.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(WalletCoreService.class)
@ActiveProfiles("test")
class WalletCoreServiceTest {

    @Autowired
    private WalletCoreService walletCoreService;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void createIfMissing_newUser_persistsAndReturnsCreatedTrue() {
        UUID intuitAccountId = UUID.randomUUID();

        WalletCoreService.CreateWalletResult result = walletCoreService.createIfMissing(intuitAccountId);

        assertThat(result.created()).isTrue();
        assertThat(result.wallet().walletId()).isNotNull();
        assertThat(result.wallet().intuitAccountId()).isEqualTo(intuitAccountId);
        assertThat(result.wallet().type()).isEqualTo("USER");
        assertThat(result.wallet().status()).isEqualTo("ACTIVE");
        assertThat(result.wallet().createdAt()).isNotNull();
        assertThat(result.wallet().updatedAt()).isNotNull();
        assertThat(walletRepository.count()).isEqualTo(1);
    }

    @Test
    void createIfMissing_existingWallet_returnsExistingAndCreatedFalse() {
        UUID intuitAccountId = UUID.randomUUID();

        WalletCoreService.CreateWalletResult first = walletCoreService.createIfMissing(intuitAccountId);
        WalletCoreService.CreateWalletResult second = walletCoreService.createIfMissing(intuitAccountId);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.wallet().walletId()).isEqualTo(first.wallet().walletId());
        assertThat(walletRepository.count()).isEqualTo(1);
    }

    @Test
    void getById_existingId_returnsView() {
        UUID intuitAccountId = UUID.randomUUID();
        WalletCoreService.CreateWalletResult created = walletCoreService.createIfMissing(intuitAccountId);

        WalletView view = walletCoreService.getById(created.wallet().walletId());

        assertThat(view.walletId()).isEqualTo(created.wallet().walletId());
        assertThat(view.intuitAccountId()).isEqualTo(intuitAccountId);
        assertThat(view.type()).isEqualTo("USER");
        assertThat(view.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getById_unknownId_throwsWalletNotFoundException() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> walletCoreService.getById(unknown))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void getByIntuitAccountId_existing_returnsView() {
        UUID intuitAccountId = UUID.randomUUID();
        WalletCoreService.CreateWalletResult created = walletCoreService.createIfMissing(intuitAccountId);

        WalletView view = walletCoreService.getByIntuitAccountId(intuitAccountId);

        assertThat(view.walletId()).isEqualTo(created.wallet().walletId());
        assertThat(view.intuitAccountId()).isEqualTo(intuitAccountId);
    }

    @Test
    void getByIntuitAccountId_unknown_throwsWalletNotFoundException() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> walletCoreService.getByIntuitAccountId(unknown))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    @Test
    void createIfMissing_concurrentInsertResolvedToWinner() {
        UUID intuitAccountId = UUID.randomUUID();
        // Simulate the racing winner having already inserted directly via the repository.
        Wallet preexisting = walletRepository.save(
                new Wallet(UUID.randomUUID(), intuitAccountId, "USER", "ACTIVE"));

        WalletCoreService.CreateWalletResult result = walletCoreService.createIfMissing(intuitAccountId);

        assertThat(result.created()).isFalse();
        assertThat(result.wallet().walletId()).isEqualTo(preexisting.getWalletId());
        assertThat(walletRepository.count()).isEqualTo(1);
    }
}
