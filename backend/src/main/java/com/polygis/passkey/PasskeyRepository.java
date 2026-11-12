package com.polygis.passkey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface PasskeyRepository extends JpaRepository<Passkey, UUID> {

    /** Locked so the signature counter is checked and advanced atomically. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Passkey> findByCredentialId(String credentialId);

    List<Passkey> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Passkey> findByIdAndUserId(UUID id, UUID userId);

    void deleteByUserId(UUID userId);
}
