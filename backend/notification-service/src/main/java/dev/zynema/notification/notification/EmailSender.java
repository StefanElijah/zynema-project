package dev.zynema.notification.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * The one place an email leaves the service.
 *
 * <p>In development the SMTP host is MailHog, so nothing leaves the machine and
 * the message is inspectable over its HTTP API. The sender is a class and not
 * a direct {@code JavaMailSender} call so the delivery step is visible in the
 * code and swappable in tests.
 */
@Slf4j
@Component
public class EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailSender(JavaMailSender mailSender,
                       @Value("${zynema.notification.from:Zynema <no-reply@zynema.dev>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(String recipient, NotificationTemplates.EmailContent content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(content.subject());
        message.setText(content.body());
        mailSender.send(message);
        log.info("Sent '{}' to {}", content.subject(), recipient);
    }
}
