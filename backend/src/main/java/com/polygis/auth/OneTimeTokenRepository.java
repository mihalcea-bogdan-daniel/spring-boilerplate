package com.polygis.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    Optional<OneTimeToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);

    /** Row-locked variant: attempt counting and single use stay correct under concurrent guesses. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeToken t where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null order by t.createdAt desc limit 1")
    Optional<OneTimeToken> lockLatestActive(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeToken t where t.codeHash = :hash and t.purpose = :purpose")
    Optional<OneTimeToken> lockByCodeHash(@Param("hash") String codeHash, @Param("purpose") OtpPurpose purpose);

    Optional<OneTimeToken> findByCodeHashAndPurpose(String codeHash, OtpPurpose purpose);

    @Modifying
    @Query("delete from OneTimeToken t where t.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("""
            update OneTimeToken t set t.consumedAt = :now
            where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null
            """)
    int invalidateActive(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose, @Param("now") Instant now);
}
