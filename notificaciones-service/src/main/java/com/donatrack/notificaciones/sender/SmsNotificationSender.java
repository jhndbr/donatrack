package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);

    @Override
    public CanalNotificacion getCanal() {
        return CanalNotificacion.SMS;
    }

    @Override
    public void enviar(String destinatario, String asunto, String mensaje) {
        log.info("[SMS-SIMULADO] Enviando SMS a {}: {}", destinatario, mensaje);
    }
}
