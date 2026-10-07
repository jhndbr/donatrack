package com.donatrack.donaciones.service;

import com.donatrack.donaciones.domain.OutboxEvent;
import com.donatrack.donaciones.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${donatrack.rabbitmq.exchange:donatrack.events.exchange}")
    private String exchangeName;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Proceso periodico que lee los eventos almacenados transaccionalmente y los publica a RabbitMQ.
     * Garantiza entrega at-least-once sin riesgo de perder eventos por rollbacks de base de datos.
     */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publicarEventosPendientes() {
        List<OutboxEvent> eventos = outboxEventRepository.findPendingEvents();
        if (eventos.isEmpty()) {
            return;
        }

        log.debug("Procesando {} eventos pendientes en el Outbox", eventos.size());

        for (OutboxEvent evento : eventos) {
            try {
                rabbitTemplate.convertAndSend(exchangeName, evento.getRoutingKey(), evento.getPayload());
                evento.marcarPublicado();
                log.info("Evento publicado a RabbitMQ: id={}, tipo={}, routingKey={}",
                        evento.getId(), evento.getEventType(), evento.getRoutingKey());
            } catch (Exception e) {
                log.error("Fallo al publicar evento outbox id={}: {}", evento.getId(), e.getMessage());
                evento.incrementarReintentos();
            }
            outboxEventRepository.save(evento);
        }
    }
}
