package com.securesoc.repository;

import com.securesoc.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    default Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        return findByUsername(usernameOrEmail).or(() -> findByEmail(usernameOrEmail));
    }

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
