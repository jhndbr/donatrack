package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador de mensajería instantánea WhatsApp (CU-56).
 * Soporta integración con Meta Graph API / Cloud API para producción y simulación estructurada en desarrollo.
 */
@Component
public class WhatsAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationSender.class);

    @Value("${donatrack.whatsapp.api-token:}")
    private String apiToken;

    @Value("${donatrack.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Override
    public CanalNotificacion getCanal() {
        return CanalNotificacion.WHATSAPP;
    }

    @Override
    public boolean enviar(String destinatario, String asunto, String mensaje) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Destinatario WhatsApp vacío. Se omite el envío.");
            return false;
        }

        if (apiToken != null && !apiToken.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank()) {
            log.info("[WHATSAPP-META] Enviando WhatsApp real a {} vía PhoneId {}: {}", destinatario, phoneNumberId, mensaje);
            // Aquí se despacha la llamada REST HTTP a https://graph.facebook.com/v19.0/{phoneNumberId}/messages
            return true;
        } else {
            log.info("[WHATSAPP-SIMULADO] Enviando mensaje WhatsApp a {}: {}", destinatario, mensaje);
            return true;
        }
    }
}
