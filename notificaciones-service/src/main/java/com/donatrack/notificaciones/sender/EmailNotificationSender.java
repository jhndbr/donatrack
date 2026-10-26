package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Despachador de correos con soporte dual: HTML institucional enriquecido y texto plano (CU-56).
 * Apunta por defecto a Mailpit en entorno local (puerto 1025).
 */
@Component
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final JavaMailSender mailSender;

    public EmailNotificationSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public CanalNotificacion getCanal() {
        return CanalNotificacion.EMAIL;
    }

    @Override
    public boolean enviar(String destinatario, String asunto, String mensaje) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Destinatario de correo vacío o nulo. Se omite el envío.");
            return false;
        }

        try {
            boolean esHtml = mensaje != null && (mensaje.contains("<html") || mensaje.contains("<!DOCTYPE html") || mensaje.contains("<div"));
            if (esHtml) {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom("notificaciones@donatrack.org", "DonaTrack Solidaria");
                helper.setTo(destinatario);
                helper.setSubject(asunto);
                helper.setText(mensaje, true);
                mailSender.send(mimeMessage);
                log.info("Email HTML enviado exitosamente a '{}' con asunto: '{}'", destinatario, asunto);
            } else {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom("notificaciones@donatrack.org");
                mail.setTo(destinatario);
                mail.setSubject(asunto);
                mail.setText(mensaje != null ? mensaje : "");
                mailSender.send(mail);
                log.info("Email de texto plano enviado exitosamente a '{}' con asunto: '{}'", destinatario, asunto);
            }
            return true;
        } catch (Exception e) {
            log.warn("Error enviando email a '{}' (verificar Mailpit/SMTP): {}", destinatario, e.getMessage());
            return false;
        }
    }
}
