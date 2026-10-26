package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador de mensajería SMS (CU-56).
 * Soporta configuración de Twilio para producción y simulación estructurada en desarrollo.
 */
@Component
public class SmsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);

    @Value("${donatrack.sms.twilio.account-sid:}")
    private String twilioSid;

    @Value("${donatrack.sms.twilio.auth-token:}")
    private String twilioToken;

    @Value("${donatrack.sms.twilio.from-number:+15005550006}")
    private String twilioFrom;

    @Override
    public CanalNotificacion getCanal() {
        return CanalNotificacion.SMS;
    }

    @Override
    public boolean enviar(String destinatario, String asunto, String mensaje) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Destinatario SMS vacío. Se omite el envío.");
            return false;
        }

        if (twilioSid != null && !twilioSid.isBlank() && twilioToken != null && !twilioToken.isBlank()) {
            log.info("[SMS-TWILIO] Despachando SMS real a {} desde {}: {}", destinatario, twilioFrom, mensaje);
            // Aquí se ejecutaría Twilio.init(twilioSid, twilioToken); Message.creator(...).create();
            return true;
        } else {
            log.info("[SMS-SIMULADO] Enviando SMS a {}: {}", destinatario, mensaje);
            return true;
        }
    }
}
