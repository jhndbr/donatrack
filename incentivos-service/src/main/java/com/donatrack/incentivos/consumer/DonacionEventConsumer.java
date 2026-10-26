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
    private final com.donatrack.incentivos.repository.RegistroEventoProcesadoRepository eventoRepo;
    private final ObjectMapper objectMapper;

    public DonacionEventConsumer(IncentivosService incentivosService,
                                 com.donatrack.incentivos.repository.RegistroEventoProcesadoRepository eventoRepo,
                                 ObjectMapper objectMapper) {
        this.incentivosService = incentivosService;
        this.eventoRepo = eventoRepo;
        this.objectMapper = objectMapper;
        if (this.objectMapper != null) {
            this.objectMapper.findAndRegisterModules();
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.incentivos-donaciones:q.incentivos.donaciones}")
    public void recibirDonacionAsignada(Object payload) {
        procesarDonacionAsignada(payload);
    }

    public void procesarDonacionAsignada(Object payload) {
        try {
            DonacionAsignadaEvent evento = parsearEvento(payload, DonacionAsignadaEvent.class);
            String claveEvento = "donacion-asignada-" + evento.getIdDonacion() + "-" + evento.getIdDonante();

            // Control de idempotencia (FT-06)
            if (eventoRepo.existsByClaveEvento(claveEvento)) {
                log.info("Incentivos AMQP: Evento ya procesado previamente (idempotencia activa): {}", claveEvento);
                return;
            }

            log.info("Incentivos AMQP: Registrando organización ayudada por donación asignada para donante id={}", evento.getIdDonante());
            if (evento.getCanalPreferidoDonante() != null || evento.getContactoPreferidoDonante() != null) {
                incentivosService.actualizarContactoPreferidoDonante(
                        evento.getIdDonante(),
                        evento.getCanalPreferidoDonante(),
                        evento.getContactoPreferidoDonante()
                );
            }
            incentivosService.registrarOrganizacionAyudadaPorAsignacion(
                    evento.getIdDonante(),
                    evento.getNombreDonante(),
                    evento.getEmailDonante(),
                    evento.getIdEntidadBeneficiaria()
            );

            eventoRepo.save(new com.donatrack.incentivos.domain.RegistroEventoProcesado(claveEvento, "DONACION_ASIGNADA"));
        } catch (Exception e) {
            log.error("Error al procesar donacion.asignada en incentivos: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.incentivos-entregas:q.incentivos.entregas}")
    public void recibirEntregaConfirmada(Object payload) {
        try {
            EntregaConfirmadaEvent evento = parsearEvento(payload, EntregaConfirmadaEvent.class);
            String claveEvento = "entrega-confirmada-" + evento.getIdEntrega();

            // Control de idempotencia (FT-06)
            if (eventoRepo.existsByClaveEvento(claveEvento)) {
                log.info("Incentivos AMQP: Evento ya procesado previamente (idempotencia activa): {}", claveEvento);
                return;
            }

            if (evento.getIdDonante() != null) {
                log.info("Incentivos AMQP: Registrando entrega confirmada para donante id={}", evento.getIdDonante());
                incentivosService.registrarEntregaExitosa(evento.getIdDonante(), evento.getEmailDonante());
            } else {
                log.info("Incentivos AMQP: Registrando entrega confirmada para donante email={}", evento.getEmailDonante());
                incentivosService.registrarEntregaExitosaPorEmail(evento.getEmailDonante());
            }

            eventoRepo.save(new com.donatrack.incentivos.domain.RegistroEventoProcesado(claveEvento, "ENTREGA_CONFIRMADA"));
        } catch (Exception e) {
            log.error("Error al procesar entrega.confirmada en incentivos: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${donatrack.rabbitmq.queues.incentivos-donaciones-registradas:q.incentivos.donaciones-registradas}")
    public void recibirDonacionRegistrada(Object payload) {
        try {
            com.donatrack.common.events.DonacionRegistradaEvent evento = parsearEvento(payload, com.donatrack.common.events.DonacionRegistradaEvent.class);
            String claveEvento = "donacion-registrada-" + evento.getIdDonacion() + "-" + evento.getIdDonante();

            if (eventoRepo.existsByClaveEvento(claveEvento)) {
                return;
            }

            log.info("Incentivos AMQP: Registrando donación en depósito para donante id={}", evento.getIdDonante());
            if (evento.getCanalPreferidoDonante() != null || evento.getContactoPreferidoDonante() != null) {
                incentivosService.actualizarContactoPreferidoDonante(
                        evento.getIdDonante(),
                        evento.getCanalPreferidoDonante(),
                        evento.getContactoPreferidoDonante()
                );
            }
            incentivosService.registrarImpactoDonacionCompleto(
                    evento.getIdDonante(),
                    evento.getNombreDonante(),
                    evento.getEmailDonante(),
                    evento.getCantidad(),
                    evento.getSubcategoria(),
                    null
            );

            eventoRepo.save(new com.donatrack.incentivos.domain.RegistroEventoProcesado(claveEvento, "DONACION_REGISTRADA"));
        } catch (Exception e) {
            log.error("Error al procesar donacion.registrada en incentivos: {}", e.getMessage(), e);
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
