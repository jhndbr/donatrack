package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationSender.class);

    @Override
    public CanalNotificacion getCanal() {
        return CanalNotificacion.WHATSAPP;
    }

    @Override
    public void enviar(String destinatario, String asunto, String mensaje) {
        log.info("[WHATSAPP-SIMULADO] Enviando mensaje WhatsApp a {}: {}", destinatario, mensaje);
    }
}
