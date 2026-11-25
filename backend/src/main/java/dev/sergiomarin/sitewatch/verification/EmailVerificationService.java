package dev.sergiomarin.sitewatch.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.sergiomarin.sitewatch.alert.AlertProperties;
import dev.sergiomarin.sitewatch.common.ApiException;
import dev.sergiomarin.sitewatch.user.AppUser;
import dev.sergiomarin.sitewatch.user.AppUserRepository;

/**
 * Proves that a user owns the address they registered with. The link carries a random token that is stored only
 * as a hash, works once and expires. Until it is followed, the account gets no alert emails (see EmailChannel).
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final String INVALID_LINK = "This verification link is invalid or has expired";

    private final EmailVerificationRepository verifications;
    private final AppUserRepository users;
    private final VerificationProperties properties;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailVerificationRepository verifications, AppUserRepository users,
            VerificationProperties properties, ApplicationEventPublisher events,
            ObjectProvider<JavaMailSender> mailSender, AlertProperties alertProperties) {
        this.verifications = verifications;
        this.users = users;
        this.properties = properties;
        this.events = events;
        this.mailSender = mailSender;
        this.from = alertProperties.mailFrom();
    }

    /** Creates a fresh link for the user, replacing any earlier one, and emails it once the transaction commits. */
    @Transactional
    public void request(AppUser user) {
        verifications.deleteByUserId(user.getId());
        String token = newToken();
        verifications.save(new EmailVerification(user.getId(), hash(token), Instant.now().plus(properties.ttl())));
        events.publishEvent(new VerificationRequested(user.getEmail(), token));
    }

    /** The "send it again" button: refused when the address is already verified or the last email is too recent. */
    @Transactional
    public void resend(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.isEmailVerified()) {
            throw ApiException.conflict("Email is already verified");
        }
        verifications.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .filter(last -> last.getCreatedAt().plus(properties.resendInterval()).isAfter(Instant.now()))
                .ifPresent(last -> {
                    throw ApiException.tooManyRequests("Wait a minute before asking for another email");
                });
        request(user);
    }

    @Transactional
    public void verify(String token) {
        EmailVerification verification = verifications.findByTokenHash(hash(token))
                .filter(v -> v.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest(INVALID_LINK));
        AppUser user = users.findById(verification.getUserId()).orElseThrow(() -> ApiException.badRequest(INVALID_LINK));
        user.markEmailVerified();
        verifications.deleteByUserId(user.getId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void send(VerificationRequested event) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("SMTP is not configured: the verification email for a new account was not sent");
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(event.email());
        mail.setSubject("[Sitewatch] Confirm your email address");
        mail.setText("""
                Confirm that this is your email address to receive Sitewatch alerts:

                %s/verify-email?token=%s

                The link works once and expires in %d hours. If you did not create a Sitewatch account, ignore this message.

                -- \nSitewatch · https://monitor.sergiomarin.dev""".formatted(properties.panelUrl(), event.token(),
                properties.ttl().toHours()));
        try {
            sender.send(mail);
        } catch (MailException e) {
            // The account already exists; the user can ask for another email from the panel.
            log.warn("Could not send the verification email: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${sitewatch.verification.cleanup-cron:0 45 3 * * *}")
    @Transactional
    void deleteExpired() {
        verifications.deleteByExpiresAtBefore(Instant.now());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
