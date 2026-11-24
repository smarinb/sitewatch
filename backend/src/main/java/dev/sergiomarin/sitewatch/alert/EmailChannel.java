package dev.sergiomarin.sitewatch.alert;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import dev.sergiomarin.sitewatch.user.AppUser;

/** Sends to the account email. Available once SMTP is configured ({@code SPRING_MAIL_HOST}…). */
@Component
class EmailChannel implements AlertChannel {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    EmailChannel(ObjectProvider<JavaMailSender> mailSender, AlertProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.mailFrom();
    }

    @Override
    public String name() {
        return "email";
    }

    @Override
    public boolean available() {
        return mailSender.getIfAvailable() != null;
    }

    @Override
    public boolean enabledFor(AppUser user) {
        return user.isEmailAlerts();
    }

    @Override
    public void send(AppUser user, AlertMessage message) throws AlertDeliveryException {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(user.getEmail());
        mail.setSubject("[Sitewatch] " + message.subject());
        mail.setText(message.body() + "\n\n-- \nSitewatch · https://monitor.sergiomarin.dev");
        try {
            mailSender.getObject().send(mail);
        } catch (MailException e) {
            throw new AlertDeliveryException("SMTP delivery failed: " + e.getMessage(), e);
        }
    }
}
