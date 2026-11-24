package com.timecraft.notification.service;

import com.timecraft.authentication.entity.User;
import com.timecraft.common.dto.PageResponse;
import com.timecraft.config.AppProperties;
import com.timecraft.exception.ResourceNotFoundException;
import com.timecraft.notification.entity.EmailLog;
import com.timecraft.notification.entity.Notification;
import com.timecraft.notification.repository.EmailLogRepository;
import com.timecraft.notification.repository.NotificationRepository;
import com.timecraft.order.entity.Order;
import com.timecraft.order.service.InvoicePdfService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Manages customer in-app notifications and outbound HTML emails with persistent delivery logs.
 * Sensitive tokens are never stored in email_logs (only template name, subject and status).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailLogRepository emailLogRepository;
    private final InvoicePdfService invoicePdfService;
    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public record NotificationDto(
            Long id,
            String type,
            String title,
            String message,
            String link,
            boolean read,
            Instant createdAt
    ) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(),
                    n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt());
        }
    }

    @Transactional
    public void createNotification(User user, String type, String title, String message, String link) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> listForUser(Long userId, Pageable pageable) {
        return PageResponse.of(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationDto markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            n = notificationRepository.save(n);
        }
        return NotificationDto.from(n);
    }

    @Transactional
    public boolean sendVerificationOtpEmail(User user, String otp) {
        if (properties.mail().logLinks()) {
            log.info("[DEV MAIL] Email verification OTP for {}: {}", user.getEmail(), otp);
        }
        String verifyUrl = properties.frontendBaseUrl() + "/verify-otp?email=" + user.getEmail();
        String html = buildEmailHtml(
                "Your TimeCraft Verification Code",
                "Welcome to TimeCraft, " + escape(user.getFirstName()) + ".",
                "Use the 6-digit verification code below to activate your account. This code expires in 10 minutes:<br><br>"
                        + "<div style=\"font-size:32px;letter-spacing:10px;font-weight:700;color:#1A1A1A;background:#F0EFEA;"
                        + "padding:16px 24px;text-align:center;border:1px solid #E5E2DC;font-family:monospace;\">"
                        + escape(otp) + "</div>",
                "Enter Verification Code",
                verifyUrl
        );
        return dispatchEmail(user, user.getEmail(), "Your TimeCraft verification code: " + otp, "EMAIL_VERIFICATION_OTP", html, "USER", user.getId());
    }

    @Transactional
    public boolean sendPasswordResetOtpEmail(User user, String otp) {
        if (properties.mail().logLinks()) {
            log.info("[DEV MAIL] Password reset OTP for {}: {}", user.getEmail(), otp);
        }
        String resetUrl = properties.frontendBaseUrl() + "/pages/reset-password.html?email=" + user.getEmail();
        String html = buildEmailHtml(
                "Reset Your TimeCraft Password",
                "Hello " + escape(user.getFirstName()) + ",",
                "Use this 6-digit security code to reset your TimeCraft password. Valid for 10 minutes:<br><br>"
                        + "<div style=\"font-size:32px;letter-spacing:10px;font-weight:700;color:#1A1A1A;background:#F0EFEA;"
                        + "padding:16px 24px;text-align:center;border:1px solid #E5E2DC;font-family:monospace;\">"
                        + escape(otp) + "</div>",
                "Reset Password",
                resetUrl
        );
        return dispatchEmail(user, user.getEmail(), "Your TimeCraft password reset code: " + otp, "PASSWORD_RESET_OTP", html, "USER", user.getId());
    }

    @Transactional
    public void sendVerificationEmail(User user, String rawToken) {
        String link = properties.frontendBaseUrl() + "/pages/login.html?verify=" + rawToken;
        if (properties.mail().logLinks()) {
            log.info("[DEV MAIL] Email verification link for {}: {}", user.getEmail(), link);
        }
        String html = buildEmailHtml(
                "Verify your TimeCraft email",
                "Welcome to TimeCraft, " + escape(user.getFirstName()) + ".",
                "Please confirm your email address within 24 hours to complete your profile.",
                "Verify Email",
                link
        );
        dispatchEmail(user, user.getEmail(), "Verify your TimeCraft account", "EMAIL_VERIFICATION", html, "USER", user.getId());
    }

    @Transactional
    public void sendPasswordResetEmail(User user, String rawToken) {
        String link = properties.frontendBaseUrl() + "/pages/reset-password.html?token=" + rawToken;
        if (properties.mail().logLinks()) {
            log.info("[DEV MAIL] Password reset link for {}: {}", user.getEmail(), link);
        }
        String html = buildEmailHtml(
                "Reset your TimeCraft password",
                "Hello " + escape(user.getFirstName()) + ",",
                "We received a request to reset your TimeCraft password. This link expires in 30 minutes.",
                "Reset Password",
                link
        );
        dispatchEmail(user, user.getEmail(), "Reset your TimeCraft password", "PASSWORD_RESET", html, "USER", user.getId());
    }

    @Transactional
    public void notifyOrderConfirmed(Order order) {
        createNotification(order.getUser(), "ORDER_CONFIRMED",
                "Order " + order.getOrderNumber() + " confirmed",
                "Your order of ₹" + order.getGrandTotal() + " (" + order.getPaymentMethod() + ") has been confirmed. Your tax invoice has been emailed.",
                "/pages/order-details.html?order=" + order.getOrderNumber());

        String link = properties.frontendBaseUrl() + "/pages/order-details.html?order=" + order.getOrderNumber();
        String invoiceHtml = invoicePdfService.buildHtmlInvoice(order, link);
        byte[] pdfBytes = null;
        try {
            pdfBytes = invoicePdfService.generateInvoice(order);
        } catch (Exception ex) {
            log.warn("Could not generate PDF invoice attachment for order {}: {}", order.getOrderNumber(), ex.getMessage());
        }
        String pdfFilename = "TimeCraft-Invoice-" + order.getOrderNumber() + ".pdf";
        if (properties.mail().logLinks()) {
            log.info("[DEV MAIL] Order Invoice email prepared for {} (Order {}, Total ₹{}, PDF {} bytes)",
                    order.getCustomerEmail(), order.getOrderNumber(), order.getGrandTotal(),
                    pdfBytes != null ? pdfBytes.length : 0);
        }
        dispatchEmailWithAttachment(order.getUser(), order.getCustomerEmail(),
                "TimeCraft Tax Invoice & Order Confirmation · " + order.getOrderNumber(),
                "ORDER_INVOICE_CONFIRMATION", invoiceHtml, "ORDER", order.getId(),
                pdfFilename, pdfBytes, "application/pdf");
    }

    @Transactional
    public void notifyPaymentFailed(Order order, String reason) {
        createNotification(order.getUser(), "PAYMENT_FAILED",
                "Payment unsuccessful for " + order.getOrderNumber(),
                "We could not complete your payment. You can retry before your reservation expires.",
                "/pages/order-details.html?order=" + order.getOrderNumber());

        String link = properties.frontendBaseUrl() + "/pages/order-details.html?order=" + order.getOrderNumber();
        String html = buildEmailHtml(
                "Payment unsuccessful · " + order.getOrderNumber(),
                "Hello " + escape(order.getCustomerName()) + ",",
                "Your payment attempt for order " + order.getOrderNumber() + " did not complete ("
                        + escape(reason != null ? reason : "declined") + ").",
                "Review Order",
                link
        );
        dispatchEmail(order.getUser(), order.getCustomerEmail(),
                "Payment unsuccessful · " + order.getOrderNumber(),
                "PAYMENT_FAILURE", html, "ORDER", order.getId());
    }

    @Transactional
    public void notifyOrderStatusChanged(Order order, String headline, String details) {
        createNotification(order.getUser(), "ORDER_STATUS",
                headline + " · " + order.getOrderNumber(),
                details,
                "/pages/order-details.html?order=" + order.getOrderNumber());

        String link = properties.frontendBaseUrl() + "/pages/order-details.html?order=" + order.getOrderNumber();
        String html = buildEmailHtml(headline, "Hello " + escape(order.getCustomerName()) + ",", details, "Track Order", link);
        dispatchEmail(order.getUser(), order.getCustomerEmail(),
                "TimeCraft · " + headline + " (" + order.getOrderNumber() + ")",
                "ORDER_STATUS_UPDATE", html, "ORDER", order.getId());
    }

    @Transactional
    public void notifyReturnAndRefund(User user, String subject, String title, String message, Long relatedId) {
        createNotification(user, "RETURN_REFUND", title, message, "/pages/orders.html");
        String link = properties.frontendBaseUrl() + "/pages/orders.html";
        String html = buildEmailHtml(title, "Hello " + escape(user.getFirstName()) + ",", message, "View Orders", link);
        dispatchEmail(user, user.getEmail(), "TimeCraft · " + subject, "RETURN_REFUND", html, "RETURN", relatedId);
    }

    private boolean dispatchEmail(User user, String recipient, String subject, String template,
                                  String html, String relatedType, Long relatedId) {
        return dispatchEmailWithAttachment(user, recipient, subject, template, html, relatedType, relatedId, null, null, null);
    }

    private boolean dispatchEmailWithAttachment(User user, String recipient, String subject, String template,
                                                String html, String relatedType, Long relatedId,
                                                String attachmentName, byte[] attachmentBytes, String attachmentMime) {
        EmailLog logEntry = EmailLog.builder()
                .user(user)
                .recipient(recipient)
                .subject(subject)
                .template(template)
                .status("PENDING")
                .relatedType(relatedType)
                .relatedId(relatedId)
                .build();

        if (!properties.mail().enabled()) {
            logEntry.setStatus("SKIPPED_DISABLED");
            emailLogRepository.save(logEntry);
            return false;
        }

        boolean sent = false;
        try {
            boolean hasAttachment = attachmentName != null && attachmentBytes != null && attachmentBytes.length > 0;
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, hasAttachment, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            if (hasAttachment) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachmentBytes),
                        attachmentMime != null ? attachmentMime : "application/pdf");
            }
            mailSender.send(message);
            logEntry.setStatus("SENT");
            logEntry.setSentAt(Instant.now());
            sent = true;
        } catch (Exception ex) {
            log.warn("Email delivery failed for {} ({}): {}", recipient, template, ex.getMessage());
            logEntry.setStatus("FAILED");
            String err = ex.getMessage();
            logEntry.setErrorMessage(err != null && err.length() > 500 ? err.substring(0, 500) : err);
        }
        emailLogRepository.save(logEntry);
        return sent;
    }

    private String buildEmailHtml(String heading, String greeting, String body, String ctaLabel, String ctaUrl) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <body style="margin:0;padding:32px;background:#FAF9F6;color:#1A1A1A;font-family:'DM Sans',Helvetica,Arial,sans-serif;">
                  <div style="max-width:560px;margin:0 auto;background:#FFFFFF;border:1px solid #E5E2DC;padding:40px;">
                    <div style="font-family:Georgia,serif;font-size:22px;letter-spacing:0.12em;text-transform:uppercase;border-bottom:1px solid #E5E2DC;padding-bottom:16px;margin-bottom:28px;">
                      TIMECRAFT
                    </div>
                    <h1 style="font-family:Georgia,serif;font-weight:400;font-size:24px;margin:0 0 16px;">%s</h1>
                    <p style="font-size:15px;line-height:1.6;margin:0 0 12px;">%s</p>
                    <p style="font-size:15px;line-height:1.6;color:#4A4945;margin:0 0 28px;">%s</p>
                    <p style="margin:0 0 28px;">
                      <a href="%s" style="display:inline-block;background:#1A1A1A;color:#FAF9F6;text-decoration:none;padding:12px 24px;font-size:13px;letter-spacing:0.08em;text-transform:uppercase;">%s</a>
                    </p>
                    <div style="border-top:1px solid #E5E2DC;padding-top:16px;font-size:12px;color:#7A776E;">
                      TimeCraft Fine Horology · Complimentary insured delivery
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(escape(heading), greeting, body, ctaUrl, escape(ctaLabel));
    }

    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
