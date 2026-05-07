package com.intuit.walletservice.businesslogic.core;

import com.intuit.walletservice.dal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(UserCoreService.class)
@ActiveProfiles("test")
class UserCoreServiceTest {

    @Autowired
    private UserCoreService userCoreService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createUser_persistsRowAndReturnsCreatedTrue() {
        UserCoreService.CreateUserResult result =
                userCoreService.createUser("alice@example.com", "CONSUMER", "us-east-1");

        assertThat(result.created()).isTrue();
        assertThat(result.user().intuitAccountId()).isNotNull();
        assertThat(result.user().email()).isEqualTo("alice@example.com");
        assertThat(result.user().role()).isEqualTo("CONSUMER");
        assertThat(result.user().homeRegion()).isEqualTo("us-east-1");
        assertThat(result.user().createdAt()).isNotNull();
        assertThat(result.user().updatedAt()).isNotNull();
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void createUser_duplicateEmailExactCase_returnsExistingRowAndCreatedFalse() {
        UserCoreService.CreateUserResult first =
                userCoreService.createUser("alice@example.com", "CONSUMER", "us-east-1");
        UserCoreService.CreateUserResult second =
                userCoreService.createUser("alice@example.com", "MERCHANT", "us-west-2");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.user().intuitAccountId()).isEqualTo(first.user().intuitAccountId());
        assertThat(second.user().role()).isEqualTo("CONSUMER");
        assertThat(second.user().homeRegion()).isEqualTo("us-east-1");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void createUser_duplicateEmailDifferentCase_returnsExistingRowAndCreatedFalse() {
        UserCoreService.CreateUserResult first =
                userCoreService.createUser("Alice@Example.com", "CONSUMER", "us-east-1");
        UserCoreService.CreateUserResult second =
                userCoreService.createUser("alice@example.com", "CONSUMER", "us-east-1");

        assertThat(second.created()).isFalse();
        assertThat(second.user().intuitAccountId()).isEqualTo(first.user().intuitAccountId());
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void getUser_existingId_returnsView() {
        UserCoreService.CreateUserResult created =
                userCoreService.createUser("alice@example.com", "CONSUMER", "us-east-1");

        UserView view = userCoreService.getUser(created.user().intuitAccountId());

        assertThat(view.intuitAccountId()).isEqualTo(created.user().intuitAccountId());
        assertThat(view.email()).isEqualTo("alice@example.com");
        assertThat(view.role()).isEqualTo("CONSUMER");
        assertThat(view.homeRegion()).isEqualTo("us-east-1");
        assertThat(view.createdAt()).isEqualTo(created.user().createdAt());
        assertThat(view.updatedAt()).isEqualTo(created.user().updatedAt());
    }

    @Test
    void getUser_unknownId_throwsUserNotFoundException() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> userCoreService.getUser(unknown))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }
}
