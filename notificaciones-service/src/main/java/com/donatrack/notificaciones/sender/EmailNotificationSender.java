package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

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
    public void enviar(String destinatario, String asunto, String mensaje) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom("notificaciones@donatrack.org");
            mail.setTo(destinatario);
            mail.setSubject(asunto);
            mail.setText(mensaje);
            mailSender.send(mail);
            log.info("Email enviado exitosamente a '{}' con asunto: '{}'", destinatario, asunto);
        } catch (Exception e) {
            log.warn("Error enviando email a '{}' (verificar Mailpit): {}", destinatario, e.getMessage());
        }
    }
}
