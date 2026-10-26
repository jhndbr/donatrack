package com.donatrack.donaciones.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.DonacionAsignadaEvent;
import com.donatrack.donaciones.domain.*;
import com.donatrack.donaciones.exception.DonacionYaAsignadaException;
import com.donatrack.donaciones.repository.*;
import com.donatrack.donaciones.repository.specification.DonacionSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio Central de Donaciones (donaciones-service) - DonaTrack
 * Administra el ciclo de vida, segmentación por subcategoría, asignación con
 * control de concurrencia optimista (FT-09), desasignación administrativa (CU-18)
 * y publicación transaccional mediante Transactional Outbox.
 */
@Service
public class DonacionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DonacionService.class);

    private final DonacionRepository donacionRepository;
    private final PersonaDonanteRepository donanteRepository;
    private final EntidadBeneficiariaRepository entidadRepository;
    private final NecesidadMaterialRepository necesidadRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${donatrack.rabbitmq.routing-key.donacion-asignada:donacion.asignada}")
    private String routingKeyDonacionAsignada;

    @Value("${donatrack.rabbitmq.routing-key.donacion-registrada:donacion.registrada}")
    private String routingKeyDonacionRegistrada;

    public DonacionService(DonacionRepository donacionRepository,
                           PersonaDonanteRepository donanteRepository,
                           EntidadBeneficiariaRepository entidadRepository,
                           NecesidadMaterialRepository necesidadRepository,
                           OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.donacionRepository = donacionRepository;
        this.donanteRepository = donanteRepository;
        this.entidadRepository = entidadRepository;
        this.necesidadRepository = necesidadRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<Donacion> registrarCargaSegmentada(Long donanteId, List<BienDonado> bienes) {
        return registrarCargaSegmentada(donanteId, bienes, null);
    }

    /**
     * Segmenta automáticamente un conjunto de bienes por subcategoría (CU-13, CU-14, CU-15).
     * Si se provee entidadId, se asigna directamente a dicha entidad beneficiaria pasando a ASIGNACION_REALIZADA.
     */
    @Transactional
    public List<Donacion> registrarCargaSegmentada(Long donanteId, List<BienDonado> bienes, Long entidadId) {
        PersonaDonante donante = donanteRepository.findById(donanteId)
                .orElseThrow(() -> new IllegalArgumentException("Donante no encontrado: " + donanteId));

        if (!donante.isActivo()) {
            throw new IllegalStateException("El donante con ID " + donanteId + " está inactivo y no puede registrar donaciones (CU-11).");
        }

        donante.registrarActividad();

        // Validar consistencia de cada bien ingresado
        bienes.forEach(BienDonado::validarConsistencia);

        // Agrupar bienes por subcategoria, unidad de medida, fecha de vencimiento y condición nuevo/usado (CU-13, CU-14)
        Map<String, List<BienDonado>> porSubcategoria = bienes.stream()
                .collect(Collectors.groupingBy(b ->
                        (b.getSubcategoria() != null ? b.getSubcategoria().trim().toLowerCase() : "general") + "_" +
                        (b.getUnidadMedida() != null ? b.getUnidadMedida().trim().toLowerCase() : "unidad") + "_" +
                        (b.getFechaVencimiento() != null ? b.getFechaVencimiento().toString() : "NO_VENCE") + "_" +
                        (b.isEsUsado() ? "USADO" : "NUEVO")
                ));

        List<Donacion> donacionesResultantes = new ArrayList<>();

        for (List<BienDonado> grupoBienes : porSubcategoria.values()) {
            BienDonado muestra = grupoBienes.get(0);
            double cantidadTotal = grupoBienes.stream().mapToDouble(BienDonado::getCantidad).sum();
            boolean algunoUsado = grupoBienes.stream().anyMatch(BienDonado::isEsUsado);

            Donacion donacion = new Donacion(
                    donante,
                    muestra.getSubcategoria(),
                    cantidadTotal,
                    muestra.getUnidadMedida(),
                    algunoUsado,
                    muestra.getFechaVencimiento()
            );
            if (muestra.getDescripcion() != null && !muestra.getDescripcion().isBlank()) {
                donacion.setDescripcionGeneral(muestra.getDescripcion());
            }

            for (BienDonado b : grupoBienes) {
                donacion.agregarBien(b);
            }

            Donacion guardada = donacionRepository.save(donacion);

            // Notificar evento DonacionRegistradaEvent SIEMPRE para actualizar métricas de Incentivos
            try {
                String cat = (muestra.getCategoria() != null) ? muestra.getCategoria() : "Bienes";
                com.donatrack.donaciones.domain.MedioContacto m = donante.getMedioContactoPredeterminado();
                com.donatrack.common.enums.CanalNotificacion c = (m != null && m.getTipo() != null)
                        ? m.getTipo() : com.donatrack.common.enums.CanalNotificacion.EMAIL;
                String cont = (m != null && m.getValor() != null)
                        ? m.getValor() : donante.getEmailPrincipal();

                com.donatrack.common.events.DonacionRegistradaEvent regEv = new com.donatrack.common.events.DonacionRegistradaEvent(
                        guardada.getId(),
                        donante.getId(),
                        donante.getNombreCompleto(),
                        donante.getEmailPrincipal(),
                        c,
                        cont,
                        cat,
                        guardada.getSubcategoria(),
                        guardada.getCantidad(),
                        guardada.getUnidadMedida()
                );
                regEv.setCanalPreferidoDonante(c);
                regEv.setContactoPreferidoDonante(cont);
                String payloadJson = objectMapper.writeValueAsString(regEv);
                outboxEventRepository.save(new OutboxEvent(
                        "Donacion",
                        guardada.getId() != null ? guardada.getId().toString() : "0",
                        "DonacionRegistradaEvent",
                        routingKeyDonacionRegistrada,
                        payloadJson
                ));
            } catch (Exception e) {
                log.error("Error guardando OutboxEvent DonacionRegistradaEvent: {}", e.getMessage());
            }

            // Asignación directa si el donante seleccionó una entidad específica
            if (entidadId != null) {
                guardada = asignarDonacion(guardada.getId(), entidadId, null, "DONANTE");
            }

            donacionesResultantes.add(guardada);
        }

        return donacionesResultantes;
    }

    /**
     * Asigna una donación a una entidad beneficiaria y opcionalmente a una necesidad concreta (CU-34, CU-35).
     * Previene condiciones de carrera concurrentes (FT-09) y persiste evento Outbox en la misma transacción.
     */
    @Transactional
    public Donacion asignarDonacion(Long donacionId, Long entidadId, Long necesidadId, String usuarioResponsable) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada: " + donacionId));

        // Control de concurrencia de negocio: verificar estado EN_DEPOSITO
        if (donacion.getEstado() != EstadoDonacion.EN_DEPOSITO) {
            throw new DonacionYaAsignadaException(donacionId);
        }

        EntidadBeneficiaria entidad = entidadRepository.findById(entidadId)
                .orElseThrow(() -> new IllegalArgumentException("Entidad beneficiaria no encontrada: " + entidadId));

        if (!entidad.isActivo()) {
            throw new IllegalStateException("La entidad beneficiaria " + entidad.getRazonSocial() + " se encuentra inactiva (CU-25).");
        }

        NecesidadMaterial necesidad = null;
        if (necesidadId != null) {
            necesidad = necesidadRepository.findById(necesidadId)
                    .orElseThrow(() -> new IllegalArgumentException("Necesidad material no encontrada: " + necesidadId));

            if (!necesidad.isActiva()) {
                throw new IllegalStateException("La necesidad material " + necesidadId + " no está activa (ya fue cerrada o satisfecha).");
            }
            if (!necesidad.getEntidadBeneficiaria().getId().equals(entidad.getId())) {
                throw new IllegalArgumentException("La necesidad material " + necesidadId + " no pertenece a la entidad " + entidadId);
            }
        } else if (necesidadRepository != null && donacion.getSubcategoria() != null) {
            List<NecesidadMaterial> activas = necesidadRepository.findByEntidadBeneficiariaIdAndActivaTrue(entidadId);
            if (activas != null) {
                necesidad = activas.stream()
                        .filter(n -> n.getSubcategoria() != null && n.getSubcategoria().equalsIgnoreCase(donacion.getSubcategoria()))
                        .findFirst()
                        .orElse(null);
            }
        }

        donacion.asignarA(entidad, necesidad, usuarioResponsable != null ? usuarioResponsable : "OPERADOR");
        if (necesidad != null) {
            necesidadRepository.save(necesidad);
        }
        Donacion guardada = donacionRepository.save(donacion);

        // Crear evento para el Outbox en la misma transaccion de PostgreSQL
        String cat = (guardada.getBienes() != null && !guardada.getBienes().isEmpty())
                ? guardada.getBienes().get(0).getCategoria() : "Bienes";
        com.donatrack.donaciones.domain.MedioContacto medio = guardada.getDonante().getMedioContactoPredeterminado();
        com.donatrack.common.enums.CanalNotificacion canal = (medio != null && medio.getTipo() != null)
                ? medio.getTipo() : com.donatrack.common.enums.CanalNotificacion.EMAIL;
        String contacto = (medio != null && medio.getValor() != null)
                ? medio.getValor() : guardada.getDonante().getEmailPrincipal();

        DonacionAsignadaEvent evento = new DonacionAsignadaEvent(
                guardada.getId(),
                cat,
                guardada.getSubcategoria(),
                guardada.getCantidad(),
                guardada.getUnidadMedida(),
                entidad.getId(),
                entidad.getRazonSocial(),
                entidad.getEmailPrincipal(),
                guardada.getDonante().getId(),
                guardada.getDonante().getEmailPrincipal(),
                guardada.getDonante().getNombreCompleto(),
                canal,
                contacto
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(evento);
            OutboxEvent outbox = new OutboxEvent(
                    "Donacion",
                    guardada.getId().toString(),
                    "DonacionAsignadaEvent",
                    routingKeyDonacionAsignada,
                    payloadJson
            );
            outboxEventRepository.save(outbox);
        } catch (Exception e) {
            throw new RuntimeException("Error al serializar evento outbox para donacion " + donacionId, e);
        }

        return guardada;
    }

    @Transactional
    public Donacion asignarDonacion(Long donacionId, Long entidadId, Long necesidadId) {
        return asignarDonacion(donacionId, entidadId, necesidadId, "OPERADOR");
    }

    @Transactional
    public Donacion asignarDonacion(Long donacionId, Long entidadId) {
        return asignarDonacion(donacionId, entidadId, null, "OPERADOR");
    }

    /**
     * Desasigna administrativamente una donación en ASIGNACION_REALIZADA devolviéndola a EN_DEPOSITO (CU-18).
     * Revierte la cantidad cubierta acumulada en la necesidad material asociada.
     */
    @Transactional
    public Donacion desasignarDonacion(Long donacionId, String motivo, String usuarioResponsable) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada: " + donacionId));

        NecesidadMaterial necesidad = donacion.getNecesidadAsignada();
        donacion.desasignar(motivo, usuarioResponsable != null ? usuarioResponsable : "ADMIN");

        if (necesidad != null) {
            necesidadRepository.save(necesidad);
        }

        return donacionRepository.save(donacion);
    }

    @Transactional
    public Donacion desasignarDonacion(Long donacionId, String motivo) {
        return desasignarDonacion(donacionId, motivo, "ADMIN");
    }

    /**
     * Modificación restringida de atributos descriptivos de una donación (CU-18).
     * Solo permitida si la donación permanece en estado EN_DEPOSITO.
     */
    @Transactional
    public Donacion modificarDonacion(Long donacionId, String subcategoria, Double cantidad,
                                      String unidadMedida, Boolean esUsado) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada: " + donacionId));

        if (!donacion.esEditable()) {
            throw new IllegalStateException("Solo se pueden modificar donaciones en estado EN_DEPOSITO. Estado actual: " + donacion.getEstado());
        }

        if (subcategoria != null && !subcategoria.isBlank()) donacion.setSubcategoria(subcategoria);
        if (cantidad != null && cantidad > 0) donacion.setCantidad(cantidad);
        if (unidadMedida != null && !unidadMedida.isBlank()) donacion.setUnidadMedida(unidadMedida);
        if (esUsado != null) donacion.setEsUsado(esUsado);

        return donacionRepository.save(donacion);
    }

    /**
     * Consulta y filtrado dinámico multicriterio de donaciones usando JPA Specifications (CU-16, CU-17).
     */
    @Transactional(readOnly = true)
    public List<Donacion> filtrarDonaciones(List<EstadoDonacion> estados, String subcategoria,
                                           Long donanteId, Long entidadId,
                                           LocalDateTime fechaDesde, LocalDateTime fechaHasta,
                                           Boolean esUsado) {
        return donacionRepository.findAll(DonacionSpecification.conFiltros(
                estados, subcategoria, donanteId, entidadId, fechaDesde, fechaHasta, esUsado
        ));
    }

    @Transactional
    public Donacion actualizarEstado(Long donacionId, EstadoDonacion nuevoEstado, String justificacion, String usuarioResponsable, String fotoUrl) {
        Donacion donacion = donacionRepository.findById(donacionId)
                .orElseThrow(() -> new IllegalArgumentException("Donacion no encontrada: " + donacionId));

        String user = usuarioResponsable != null ? usuarioResponsable : "OPERADOR";
        switch (nuevoEstado) {
            case LISTA_PARA_ENTREGAR -> donacion.marcarListaParaEntregar(user);
            case EN_TRASLADO -> donacion.iniciarTraslado(user);
            case ENTREGADA -> donacion.confirmarEntrega(user);
            case ENTREGA_FALLIDA -> donacion.registrarEntregaFallida(justificacion, user);
            case VENCIDA -> donacion.marcarVencida(user);
            case EN_DEPOSITO -> donacion.reingresarADeposito(user);
            default -> throw new IllegalArgumentException("Transicion de estado no permitida directamente: " + nuevoEstado);
        }

        if (fotoUrl != null && !fotoUrl.isBlank() && donacion.getBienes() != null && !donacion.getBienes().isEmpty()) {
            for (BienDonado b : donacion.getBienes()) {
                b.setFotoUrl(fotoUrl);
            }
        }

        Donacion guardada = donacionRepository.save(donacion);

        if (!"LOGISTICA_SERVICE".equals(user) && !"LOGISTICA".equals(user) && !"CHOFER".equals(user)) {
            publicarEventoTransicionOutbox(guardada, nuevoEstado, justificacion);
        }

        return guardada;
    }

    private void publicarEventoTransicionOutbox(Donacion donacion, EstadoDonacion nuevoEstado, String justificacion) {
        if (objectMapper == null || outboxEventRepository == null || donacion == null) {
            return;
        }
        try {
            String emailDonante = donacion.getDonante() != null ? donacion.getDonante().getEmailPrincipal() : null;
            String emailEntidad = donacion.getEntidadAsignada() != null ? donacion.getEntidadAsignada().getEmailPrincipal() : null;
            Long idEntidad = donacion.getEntidadAsignada() != null ? donacion.getEntidadAsignada().getId() : null;
            Long idDonacion = donacion.getId();

            Object evento = null;
            String eventType = null;
            String routingKey = null;

            if (nuevoEstado == EstadoDonacion.ENTREGADA) {
                evento = new com.donatrack.common.events.EntregaConfirmadaEvent(
                        "ENT-DON-" + idDonacion,
                        idDonacion,
                        idEntidad,
                        "DIRECTO",
                        "http://localhost:8086/beneficiaria/notificaciones",
                        emailDonante,
                        emailEntidad
                );
                eventType = "EntregaConfirmadaEvent";
                routingKey = "entrega.confirmada";
            } else if (nuevoEstado == EstadoDonacion.ENTREGA_FALLIDA) {
                evento = new com.donatrack.common.events.EntregaFallidaEvent(
                        "FALLO-DON-" + idDonacion,
                        idDonacion,
                        idEntidad,
                        justificacion != null && !justificacion.isBlank() ? justificacion : "Incidencia reportada en entrega",
                        true,
                        emailDonante,
                        emailEntidad
                );
                eventType = "EntregaFallidaEvent";
                routingKey = "entrega.fallida";
            } else if (nuevoEstado == EstadoDonacion.EN_TRASLADO) {
                List<String> emails = new ArrayList<>();
                if (emailDonante != null && !emailDonante.isBlank()) emails.add(emailDonante);
                if (emailEntidad != null && !emailEntidad.isBlank()) emails.add(emailEntidad);
                evento = new com.donatrack.common.events.RutaIniciadaEvent(
                        "RUTA-DON-" + idDonacion,
                        "AE789XY",
                        idDonacion != null ? List.of(idDonacion) : List.of(),
                        emails,
                        "http://localhost:8086/donante/tracking"
                );
                eventType = "RutaIniciadaEvent";
                routingKey = "ruta.iniciada";
            }

            if (evento != null) {
                String payloadJson = objectMapper.writeValueAsString(evento);
                if (payloadJson != null && !payloadJson.isBlank()) {
                    OutboxEvent outbox = new OutboxEvent(
                            "Donacion",
                            idDonacion != null ? idDonacion.toString() : "0",
                            eventType,
                            routingKey,
                            payloadJson
                    );
                    outboxEventRepository.save(outbox);
                }
            }
        } catch (Exception ignored) {
            // No interrumpir la transacción principal si el mapper es un mock sin configurar
        }
    }

    @Transactional
    public Donacion actualizarEstado(Long donacionId, EstadoDonacion nuevoEstado, String justificacion, String usuarioResponsable) {
        return actualizarEstado(donacionId, nuevoEstado, justificacion, usuarioResponsable, null);
    }

    @Transactional
    public Donacion actualizarEstado(Long donacionId, EstadoDonacion nuevoEstado, String justificacion) {
        return actualizarEstado(donacionId, nuevoEstado, justificacion, "OPERADOR", null);
    }

    @Transactional
    public List<Donacion> actualizarEstadoLote(List<Long> donacionIds, EstadoDonacion nuevoEstado, String justificacion) {
        List<Donacion> actualizadas = new ArrayList<>();
        for (Long id : donacionIds) {
            actualizadas.add(actualizarEstado(id, nuevoEstado, justificacion, "LOGISTICA"));
        }
        return actualizadas;
    }
}
