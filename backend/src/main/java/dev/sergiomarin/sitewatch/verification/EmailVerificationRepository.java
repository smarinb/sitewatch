package dev.sergiomarin.sitewatch.verification;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByTokenHash(String tokenHash);

    Optional<EmailVerification> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserId(Long userId);

    void deleteByExpiresAtBefore(Instant instant);
}
