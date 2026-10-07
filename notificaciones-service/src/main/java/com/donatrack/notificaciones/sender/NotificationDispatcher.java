package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Despachador polimorfico multicanal (Strategy / Factory Pattern).
 * Cumple con Open-Closed Principle (OCP) y Dependency Inversion (DIP):
 * Permite incorporar nuevos canales (ej. Push, Telegram) sin alterar la logica existente.
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final Map<CanalNotificacion, NotificationSender> senderMap = new EnumMap<>(CanalNotificacion.class);

    public NotificationDispatcher(List<NotificationSender> senders) {
        for (NotificationSender sender : senders) {
            this.senderMap.put(sender.getCanal(), sender);
        }
    }

    public void despachar(CanalNotificacion canal, String destinatario, String asunto, String mensaje) {
        NotificationSender sender = senderMap.get(canal);
        if (sender != null) {
            sender.enviar(destinatario, asunto, mensaje);
        } else {
            log.warn("No se encontro un NotificationSender registrado para el canal: {}", canal);
        }
    }

    public boolean soportaCanal(CanalNotificacion canal) {
        return senderMap.containsKey(canal);
    }
}
