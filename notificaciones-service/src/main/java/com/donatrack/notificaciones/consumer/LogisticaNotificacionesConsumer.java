package com.donatrack.notificaciones.consumer;

import com.donatrack.common.events.CamionEnPuertaEvent;
import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.common.events.RutaIniciadaEvent;
import com.donatrack.notificaciones.service.NotificacionesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class LogisticaNotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogisticaNotificacionesConsumer.class);

    private final NotificacionesService notificacionesService;
    private final ObjectMapper objectMapper;

    public LogisticaNotificacionesConsumer(NotificacionesService notificacionesService, ObjectMapper objectMapper) {
        this.notificacionesService = notificacionesService;
        this.objectMapper = objectMapper;
        if (this.objectMapper != null) {
            this.objectMapper.findAndRegisterModules();
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.ruta-iniciada:q.notificaciones.ruta-iniciada}")
    public void recibirRutaIniciada(Object payload) {
        try {
            RutaIniciadaEvent evento = parsearEvento(payload, RutaIniciadaEvent.class);
            log.info("Consumidor AMQP: Procesando ruta.iniciada para camión {}", evento.getPatenteCamion());
            notificacionesService.notificarRutaIniciada(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de ruta iniciada: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.entrega-confirmada:q.notificaciones.entrega-confirmada}")
    public void recibirEntregaConfirmada(Object payload) {
        try {
            EntregaConfirmadaEvent evento = parsearEvento(payload, EntregaConfirmadaEvent.class);
            log.info("Consumidor AMQP: Procesando entrega.confirmada para entrega id={}", evento.getIdEntrega());
            notificacionesService.notificarEntregaConfirmada(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de entrega confirmada: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.entrega-fallida:q.notificaciones.entrega-fallida}")
    public void recibirEntregaFallida(Object payload) {
        try {
            EntregaFallidaEvent evento = parsearEvento(payload, EntregaFallidaEvent.class);
            log.info("Consumidor AMQP: Procesando entrega.fallida para entrega id={}", evento.getIdEntrega());
            notificacionesService.notificarEntregaFallida(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de entrega fallida: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.camion-en-puerta:q.notificaciones.camion-en-puerta}")
    public void recibirCamionEnPuerta(Object payload) {
        try {
            CamionEnPuertaEvent evento = parsearEvento(payload, CamionEnPuertaEvent.class);
            log.info("Consumidor AMQP: Procesando camion.en-puerta para entrega id={}, entidad={}", evento.getIdEntrega(), evento.getNombreEntidad());
            notificacionesService.notificarCamionEnPuerta(evento);
        } catch (Exception e) {
            log.error("Error al procesar mensaje de camion en puerta: {}", e.getMessage(), e);
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
