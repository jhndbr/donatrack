package com.donatrack.donaciones.consumer;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.donaciones.service.DonacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor asíncrono en donaciones-service para sincronización eventual
 * de eventos emitidos por logistica-service (ARCH-01, TASK-P1-05).
 * Garantiza resiliencia distribuida en caso de caída o timeout en peticiones HTTP sincrónicas.
 */
@Component
public class LogisticaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogisticaEventConsumer.class);

    private final DonacionService donacionService;

    public LogisticaEventConsumer(DonacionService donacionService) {
        this.donacionService = donacionService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = "q.donaciones.entregas-confirmadas", durable = "true"),
                    exchange = @Exchange(value = "${donatrack.rabbitmq.exchange:donatrack.events.exchange}", type = ExchangeTypes.TOPIC),
                    key = "${donatrack.rabbitmq.routing-key.entrega-confirmada:entrega.confirmada}"
            )
    )
    public void recibirEntregaConfirmada(EntregaConfirmadaEvent evento) {
        if (evento == null || evento.getIdDonacion() == null) {
            return;
        }
        try {
            log.info("Donaciones AMQP: Sincronizando entrega confirmada asíncrona para donación id={}", evento.getIdDonacion());
            String urlEvidencia = evento.getComprobanteUrl();
            if (urlEvidencia != null && (urlEvidencia.contains("/notificaciones") || urlEvidencia.endsWith("/comprobantes"))) {
                urlEvidencia = null;
            }
            donacionService.actualizarEstado(
                    evento.getIdDonacion(),
                    EstadoDonacion.ENTREGADA,
                    "Confirmación de recepción sincronizada vía AMQP desde Logística",
                    "LOGISTICA_AMQP",
                    urlEvidencia
            );
        } catch (Exception e) {
            log.error("Error al sincronizar estado ENTREGADA para donación {}: {}", evento.getIdDonacion(), e.getMessage());
        }
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = "q.donaciones.entregas-fallidas", durable = "true"),
                    exchange = @Exchange(value = "${donatrack.rabbitmq.exchange:donatrack.events.exchange}", type = ExchangeTypes.TOPIC),
                    key = "${donatrack.rabbitmq.routing-key.entrega-fallida:entrega.fallida}"
            )
    )
    public void recibirEntregaFallida(EntregaFallidaEvent evento) {
        if (evento == null || evento.getIdDonacion() == null) {
            return;
        }
        try {
            log.info("Donaciones AMQP: Sincronizando entrega fallida asíncrona para donación id={}", evento.getIdDonacion());
            donacionService.actualizarEstado(
                    evento.getIdDonacion(),
                    EstadoDonacion.ENTREGA_FALLIDA,
                    "Incidencia registrada vía AMQP: " + (evento.getMotivoFalla() != null ? evento.getMotivoFalla() : "Sin motivo"),
                    "LOGISTICA_AMQP"
            );
        } catch (Exception e) {
            log.error("Error al sincronizar estado ENTREGA_FALLIDA para donación {}: {}", evento.getIdDonacion(), e.getMessage());
        }
    }
}
