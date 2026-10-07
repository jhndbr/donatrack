package com.donatrack.notificaciones.consumer;

import com.donatrack.common.events.DonacionAsignadaEvent;
import com.donatrack.common.events.DonanteInactivoEvent;
import com.donatrack.notificaciones.sender.EmailNotificationSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DonacionNotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(DonacionNotificacionesConsumer.class);

    private final EmailNotificationSender emailSender;
    private final ObjectMapper objectMapper;

    public DonacionNotificacionesConsumer(EmailNotificationSender emailSender, ObjectMapper objectMapper) {
        this.emailSender = emailSender;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.donacion-asignada:q.notificaciones.donacion-asignada}")
    public void recibirDonacionAsignada(Object payload) {
        try {
            DonacionAsignadaEvent evento = parsearEvento(payload, DonacionAsignadaEvent.class);
            log.info("Consumidor AMQP: Procesando evento donacion.asignada para donacion id={}", evento.getIdDonacion());

            // 1. Notificar al Donante
            if (evento.getEmailDonante() != null && !evento.getEmailDonante().isEmpty()) {
                String asunto = "¡Tu donación ha sido asignada a una entidad beneficiaria!";
                String cuerpo = String.format("Hola %s,\n\nTu donación de %.2f %s de '%s' ha sido asignada a la entidad: %s.\nPronto se coordinará la entrega logística.",
                        evento.getNombreDonante(), evento.getCantidad(), evento.getUnidadMedida(), evento.getSubcategoria(), evento.getNombreEntidad());
                emailSender.enviar(evento.getEmailDonante(), asunto, cuerpo);
            }

            // 2. Notificar a la Entidad Beneficiaria
            if (evento.getEmailEntidad() != null && !evento.getEmailEntidad().isEmpty()) {
                String asunto = "¡Nueva donación asignada a tu entidad!";
                String cuerpo = String.format("Estimados de %s,\n\nSe les ha asignado una donación de %.2f %s de '%s'.\nRecibirán novedades cuando el camión inicie la ruta.",
                        evento.getNombreEntidad(), evento.getCantidad(), evento.getUnidadMedida(), evento.getSubcategoria());
                emailSender.enviar(evento.getEmailEntidad(), asunto, cuerpo);
            }

        } catch (Exception e) {
            log.error("Error al procesar mensaje de donacion asignada: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.donante-inactivo:q.notificaciones.donante-inactivo}")
    public void recibirDonanteInactivo(Object payload) {
        try {
            DonanteInactivoEvent evento = parsearEvento(payload, DonanteInactivoEvent.class);
            log.info("Consumidor AMQP: Notificando inactividad al donante id={}", evento.getIdDonante());

            if (evento.getEmail() != null && !evento.getEmail().isEmpty()) {
                String asunto = "¡Te extrañamos en DonaTrack! Ayúdanos a seguir sumando";
                String cuerpo = String.format("Hola %s,\n\nHan pasado %d días desde tu última donación. Varias entidades beneficiarias necesitan tu apoyo. ¡Suma tu granito de arena hoy!",
                        evento.getNombre(), evento.getDiasInactivo());
                emailSender.enviar(evento.getEmail(), asunto, cuerpo);
            }
        } catch (Exception e) {
            log.error("Error al procesar mensaje de donante inactivo: {}", e.getMessage(), e);
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
