package com.donatrack.incentivos.consumer;

import com.donatrack.common.events.DonacionAsignadaEvent;
import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.incentivos.service.IncentivosService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumidor asíncrono para el Servicio de Incentivos.
 * Reacciona a eventos de donaciones asignadas y entregas confirmadas
 * para actualizar automáticamente el progreso de misiones y analítica de donantes.
 */
@Component
public class DonacionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DonacionEventConsumer.class);

    private final IncentivosService incentivosService;
    private final ObjectMapper objectMapper;

    public DonacionEventConsumer(IncentivosService incentivosService, ObjectMapper objectMapper) {
        this.incentivosService = incentivosService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.incentivos-donaciones:q.incentivos.donaciones}")
    public void recibirDonacionAsignada(Object payload) {
        try {
            DonacionAsignadaEvent evento = parsearEvento(payload, DonacionAsignadaEvent.class);
            log.info("Incentivos AMQP: Registrando donación asignada para donante id={}", evento.getIdDonante());
            incentivosService.registrarImpactoDonacion(
                    evento.getIdDonante(),
                    evento.getNombreDonante(),
                    evento.getEmailDonante(),
                    evento.getCantidad()
            );
        } catch (Exception e) {
            log.error("Error al procesar donacion.asignada en incentivos: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.incentivos-entregas:q.incentivos.entregas}")
    public void recibirEntregaConfirmada(Object payload) {
        try {
            EntregaConfirmadaEvent evento = parsearEvento(payload, EntregaConfirmadaEvent.class);
            log.info("Incentivos AMQP: Registrando entrega confirmada para donante email={}", evento.getEmailDonante());
            incentivosService.registrarEntregaExitosaPorEmail(evento.getEmailDonante());
        } catch (Exception e) {
            log.error("Error al procesar entrega.confirmada en incentivos: {}", e.getMessage(), e);
        }
    }

    private <T> T parsearEvento(Object payload, Class<T> clazz) throws Exception {
        if (clazz.isInstance(payload)) {
            return clazz.cast(payload);
        }
        if (payload instanceof org.springframework.amqp.core.Message msg) {
            String json = new String(msg.getBody(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, clazz);
        }
        if (payload instanceof String str) {
            return objectMapper.readValue(str, clazz);
        }
        if (payload instanceof byte[] bytes) {
            return objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), clazz);
        }
        return objectMapper.convertValue(payload, clazz);
    }
}
