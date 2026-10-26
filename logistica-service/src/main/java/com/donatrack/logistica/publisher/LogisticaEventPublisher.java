package com.donatrack.logistica.publisher;

import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.common.events.RutaIniciadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LogisticaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LogisticaEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${donatrack.rabbitmq.exchange:donatrack.events.exchange}")
    private String exchange;

    @Value("${donatrack.rabbitmq.routing-key.ruta-iniciada:ruta.iniciada}")
    private String routingKeyRutaIniciada;

    @Value("${donatrack.rabbitmq.routing-key.entrega-confirmada:entrega.confirmada}")
    private String routingKeyEntregaConfirmada;

    @Value("${donatrack.rabbitmq.routing-key.entrega-fallida:entrega.fallida}")
    private String routingKeyEntregaFallida;

    public LogisticaEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarRutaIniciada(RutaIniciadaEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKeyRutaIniciada, event);
            log.info("Evento RutaIniciada publicado exitosamente para ruta {}", event.getIdRuta());
        } catch (Exception e) {
            log.error("Fallo al publicar RutaIniciadaEvent para ruta {}: {}", event.getIdRuta(), e.getMessage());
        }
    }

    public void publicarEntregaConfirmada(EntregaConfirmadaEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKeyEntregaConfirmada, event);
            log.info("Evento EntregaConfirmada publicado exitosamente para entrega {} (donación {})",
                    event.getIdEntrega(), event.getIdDonacion());
        } catch (Exception e) {
            log.error("Fallo al publicar EntregaConfirmadaEvent para entrega {}: {}", event.getIdEntrega(), e.getMessage());
        }
    }

    public void publicarEntregaFallida(EntregaFallidaEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKeyEntregaFallida, event);
            log.info("Evento EntregaFallida publicado exitosamente para entrega {} (donación {})",
                    event.getIdEntrega(), event.getIdDonacion());
        } catch (Exception e) {
            log.error("Fallo al publicar EntregaFallidaEvent para entrega {}: {}", event.getIdEntrega(), e.getMessage());
        }
    }
}
