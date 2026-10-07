package com.donatrack.notificaciones.consumer;

import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.common.events.RutaIniciadaEvent;
import com.donatrack.notificaciones.sender.EmailNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LogisticaNotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogisticaNotificacionesConsumer.class);

    private final EmailNotificationSender emailSender;

    public LogisticaNotificacionesConsumer(EmailNotificationSender emailSender) {
        this.emailSender = emailSender;
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.ruta-iniciada:q.notificaciones.ruta-iniciada}")
    public void recibirRutaIniciada(RutaIniciadaEvent evento) {
        log.info("Consumidor AMQP: Procesando evento ruta.iniciada para camión {}", evento.getPatenteCamion());

        String asunto = "¡El camión ha iniciado el reparto de tu donación!";
        String cuerpo = String.format("El camión (Patente: %s) ha comenzado su recorrido.\nPuedes seguir el trayecto en tiempo real ingresando en: %s",
                evento.getPatenteCamion(), evento.getLinkSeguimiento());

        for (String email : evento.getEmailsNotificar()) {
            if (email != null && !email.isEmpty()) {
                emailSender.enviar(email, asunto, cuerpo);
            }
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.entrega-confirmada:q.notificaciones.entrega-confirmada}")
    public void recibirEntregaConfirmada(EntregaConfirmadaEvent evento) {
        log.info("Consumidor AMQP: Procesando entrega confirmada para donación id={}", evento.getIdDonacion());

        String asunto = "¡Donación entregada con éxito! Comprobante disponible";
        String cuerpo = String.format("La donación id=%d fue entregada satisfactoriamente por el camión %s.\nPuedes ver el comprobante y fotos de recepción en: %s",
                evento.getIdDonacion(), evento.getPatenteCamion(), evento.getComprobanteUrl());

        if (evento.getEmailDonante() != null && !evento.getEmailDonante().isEmpty()) {
            emailSender.enviar(evento.getEmailDonante(), asunto, cuerpo);
        }
        if (evento.getEmailEntidad() != null && !evento.getEmailEntidad().isEmpty()) {
            emailSender.enviar(evento.getEmailEntidad(), asunto, cuerpo);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.entrega-fallida:q.notificaciones.entrega-fallida}")
    public void recibirEntregaFallida(EntregaFallidaEvent evento) {
        log.info("Consumidor AMQP: Procesando entrega no completada para donación id={}", evento.getIdDonacion());

        String asunto = "Aviso sobre la entrega de tu donación";
        String cuerpo = String.format("La entrega de la donación id=%d no pudo completarse.\nMotivo registrado: %s\nReplanificable: %s",
                evento.getIdDonacion(), evento.getMotivoFalla(), evento.isReplanificable() ? "Sí" : "No");

        if (evento.getEmailDonante() != null && !evento.getEmailDonante().isEmpty()) {
            emailSender.enviar(evento.getEmailDonante(), asunto, cuerpo);
        }
        if (evento.getEmailEntidad() != null && !evento.getEmailEntidad().isEmpty()) {
            emailSender.enviar(evento.getEmailEntidad(), asunto, cuerpo);
        }
    }
}
