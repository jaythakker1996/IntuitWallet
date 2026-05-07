package com.intuit.walletservice.businesslogic.core;

import com.intuit.walletservice.dal.entity.User;
import com.intuit.walletservice.dal.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserCoreService {

    private final UserRepository userRepository;

    public UserCoreService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserView getUser(UUID intuitAccountId) {
        return userRepository.findById(intuitAccountId)
                .map(UserCoreService::toView)
                .orElseThrow(() -> new UserNotFoundException(intuitAccountId));
    }

    @Transactional
    public CreateUserResult createUser(String email, String role, String homeRegion) {
        Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            return new CreateUserResult(toView(existing.get()), false);
        }
        try {
            User saved = userRepository.save(new User(UUID.randomUUID(), email, role, homeRegion));
            return new CreateUserResult(toView(saved), true);
        } catch (DataIntegrityViolationException race) {
            // Concurrent insert collided on the unique email index; resolve to the winning row.
            User resolved = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> race);
            return new CreateUserResult(toView(resolved), false);
        }
    }

    private static UserView toView(User user) {
        return new UserView(
                user.getIntuitAccountId(),
                user.getEmail(),
                user.getRole(),
                user.getHomeRegion(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    public record CreateUserResult(UserView user, boolean created) {
    }
}
