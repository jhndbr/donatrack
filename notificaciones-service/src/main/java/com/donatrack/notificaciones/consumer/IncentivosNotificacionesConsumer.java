package com.donatrack.notificaciones.consumer;

import com.donatrack.common.events.CategoriaAscendidaEvent;
import com.donatrack.common.events.MisionCumplidaEvent;
import com.donatrack.notificaciones.service.NotificacionesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class IncentivosNotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(IncentivosNotificacionesConsumer.class);

    private final NotificacionesService notificacionesService;
    private final ObjectMapper objectMapper;

    public IncentivosNotificacionesConsumer(NotificacionesService notificacionesService, ObjectMapper objectMapper) {
        this.notificacionesService = notificacionesService;
        this.objectMapper = objectMapper;
        if (this.objectMapper != null) {
            this.objectMapper.findAndRegisterModules();
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.mision-cumplida:q.notificaciones.mision-cumplida}")
    public void recibirMisionCumplida(Object payload) {
        try {
            MisionCumplidaEvent evento = parsearEvento(payload, MisionCumplidaEvent.class);
            log.info("Consumidor AMQP: Procesando mision.cumplida para donante {}", evento.getNombreDonante());
            notificacionesService.notificarMisionCumplida(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de mision cumplida: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.categoria-ascendida:q.notificaciones.categoria-ascendida}")
    public void recibirCategoriaAscendida(Object payload) {
        try {
            CategoriaAscendidaEvent evento = parsearEvento(payload, CategoriaAscendidaEvent.class);
            log.info("Consumidor AMQP: Procesando categoria.ascendida para donante {}", evento.getNombreDonante());
            notificacionesService.notificarCategoriaAscendida(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de categoria ascendida: {}", e.getMessage(), e);
        }
    }

    private <T> T parsearEvento(Object payload, Class<T> clazz) throws Exception {
        if (clazz.isInstance(payload)) {
            return clazz.cast(payload);
        }
        String json = null;
        if (payload instanceof org.springframework.amqp.core.Message msg) {
            json = new String(msg.getBody(), StandardCharsets.UTF_8);
        } else if (payload instanceof String str) {
            json = str;
        } else if (payload instanceof byte[] bytes) {
            json = new String(bytes, StandardCharsets.UTF_8);
        }
        if (json != null) {
            String trimmed = json.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                json = objectMapper.readValue(trimmed, String.class);
            }
            return objectMapper.readValue(json, clazz);
        }
        return objectMapper.convertValue(payload, clazz);
    }
}
