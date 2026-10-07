package com.donatrack.notificaciones.consumer;

import com.donatrack.common.events.CategoriaAscendidaEvent;
import com.donatrack.common.events.MisionCumplidaEvent;
import com.donatrack.notificaciones.sender.EmailNotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class IncentivosNotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(IncentivosNotificacionesConsumer.class);

    private final EmailNotificationSender emailSender;

    public IncentivosNotificacionesConsumer(EmailNotificationSender emailSender) {
        this.emailSender = emailSender;
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.mision-cumplida:q.notificaciones.mision-cumplida}")
    public void recibirMisionCumplida(MisionCumplidaEvent evento) {
        log.info("Consumidor AMQP: Procesando mision cumplida para donante {}", evento.getNombreDonante());

        if (evento.getEmailDonante() != null && !evento.getEmailDonante().isEmpty()) {
            String asunto = "¡Felicitaciones! Has desbloqueado una nueva insignia en DonaTrack";
            String cuerpo = String.format("¡Excelente noticia %s!\n\nHas completado exitosamente la misión '%s' y obtuviste la insignia: '%s'.\nDescripción: %s\n\n¡Gracias por tu compromiso!",
                    evento.getNombreDonante(), evento.getTipoMision(), evento.getNombreInsignia(), evento.getDescripcionInsignia());
            emailSender.enviar(evento.getEmailDonante(), asunto, cuerpo);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.categoria-ascendida:q.notificaciones.categoria-ascendida}")
    public void recibirCategoriaAscendida(CategoriaAscendidaEvent evento) {
        log.info("Consumidor AMQP: Procesando ascenso de categoria para donante {}", evento.getNombreDonante());

        if (evento.getEmailDonante() != null && !evento.getEmailDonante().isEmpty()) {
            String asunto = "¡Has subido de nivel en DonaTrack!";
            String cuerpo = String.format("¡Felicidades %s!\n\nHas ascendido de la categoría %s a %s.\nTu impacto en la comunidad sigue creciendo.",
                    evento.getNombreDonante(), evento.getCategoriaPrevia(), evento.getNuevaCategoria());
            emailSender.enviar(evento.getEmailDonante(), asunto, cuerpo);
        }
    }
}
