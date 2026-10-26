package com.donatrack.notificaciones.consumer;

import com.donatrack.common.events.DonacionAsignadaEvent;
import com.donatrack.common.events.DonanteInactivoEvent;
import com.donatrack.common.events.DonanteRegistradoEvent;
import com.donatrack.notificaciones.service.NotificacionesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DonacionNotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(DonacionNotificacionesConsumer.class);

    private final NotificacionesService notificacionesService;
    private final ObjectMapper objectMapper;

    public DonacionNotificacionesConsumer(NotificacionesService notificacionesService, ObjectMapper objectMapper) {
        this.notificacionesService = notificacionesService;
        this.objectMapper = objectMapper;
        if (this.objectMapper != null) {
            this.objectMapper.findAndRegisterModules();
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.donacion-asignada:q.notificaciones.donacion-asignada}")
    public void recibirDonacionAsignada(Object payload) {
        try {
            DonacionAsignadaEvent evento = parsearEvento(payload, DonacionAsignadaEvent.class);
            log.info("Consumidor AMQP: Procesando donacion.asignada para donacion id={}", evento.getIdDonacion());
            notificacionesService.notificarDonacionAsignada(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de donacion asignada: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.donante-inactivo:q.notificaciones.donante-inactivo}")
    public void recibirDonanteInactivo(Object payload) {
        try {
            DonanteInactivoEvent evento = parsearEvento(payload, DonanteInactivoEvent.class);
            log.info("Consumidor AMQP: Procesando donante.inactivo para donante id={}", evento.getIdDonante());
            notificacionesService.notificarDonanteInactivo(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de donante inactivo: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.donante-registrado:q.notificaciones.donante-registrado}")
    public void recibirDonanteRegistrado(Object payload) {
        try {
            DonanteRegistradoEvent evento = parsearEvento(payload, DonanteRegistradoEvent.class);
            log.info("Consumidor AMQP: Procesando donante.registrado para donante id={}", evento.getIdDonante());
            notificacionesService.notificarBienvenidaDonante(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de donante registrado: {}", e.getMessage(), e);
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
