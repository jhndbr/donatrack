package com.donatrack.logistica.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.logistica.client.DonacionesServiceClient;
import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.dto.ComprobanteEntregaDto;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.EntregaFallidaRequestDto;
import com.donatrack.logistica.dto.ReplanificarRequestDto;
import com.donatrack.logistica.exception.ResourceNotFoundException;
import com.donatrack.logistica.publisher.LogisticaEventPublisher;
import com.donatrack.logistica.repository.EntregaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EntregaServiceImpl implements EntregaService {

    private static final Logger log = LoggerFactory.getLogger(EntregaServiceImpl.class);

    private final EntregaRepository entregaRepository;
    private final DonacionesServiceClient donacionesServiceClient;
    private final LogisticaEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.donatrack.logistica.repository.RutaRepository rutaRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TelemetriaService telemetriaService;

    public EntregaServiceImpl(EntregaRepository entregaRepository,
                              DonacionesServiceClient donacionesServiceClient,
                              LogisticaEventPublisher eventPublisher) {
        this.entregaRepository = entregaRepository;
        this.donacionesServiceClient = donacionesServiceClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Sincroniza el estado de la Parada correspondiente dentro del documento Ruta en MongoDB
     * para garantizar consistencia 100% entre la Hoja de Ruta del Chofer y el Portal de Entidad Beneficiaria.
     */
    private void sincronizarParadaEnRuta(Entrega entrega, String nuevoEstadoParada, String motivoOObservacion) {
        if (rutaRepository == null || entrega == null || entrega.getIdRuta() == null) {
            return;
        }
        try {
            rutaRepository.findById(entrega.getIdRuta()).ifPresent(ruta -> {
                if (ruta.getParadas() != null && entrega.getIdDonacion() != null) {
                    boolean modificado = false;
                    for (com.donatrack.logistica.domain.Parada p : ruta.getParadas()) {
                        if (entrega.getIdDonacion().equals(p.getIdDonacion())) {
                            if ("ENTREGADA".equals(nuevoEstadoParada)) {
                                p.marcarCompletada();
                            } else if ("EN_PUERTA_SEDE".equals(nuevoEstadoParada)) {
                                p.marcarArriboSede(motivoOObservacion);
                                // Actualizar posición GPS del camión a la coordenada de la sede de la entidad
                                if (telemetriaService != null && p.getLatitud() != 0.0 && p.getLongitud() != 0.0) {
                                    try {
                                        telemetriaService.registrarTelemetria(new com.donatrack.logistica.dto.TelemetriaRequestDto(
                                                ruta.getIdCamion(), ruta.getPatenteCamion(),
                                                p.getLatitud(), p.getLongitud(), 0.0,
                                                java.time.LocalDateTime.now(), ruta.getId()
                                        ));
                                    } catch (Exception ignored) {}
                                }
                            } else if ("FALLIDA".equals(nuevoEstadoParada)) {
                                p.marcarFallida(motivoOObservacion);
                            } else if ("PENDIENTE".equals(nuevoEstadoParada)) {
                                p.setCompletada(false);
                                p.setEstadoParada("PENDIENTE");
                                p.setMotivoFalla(null);
                            }
                            modificado = true;
                        }
                    }
                    if (modificado) {
                        rutaRepository.save(ruta);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("No se pudo sincronizar la parada en la ruta {}: {}", entrega.getIdRuta(), e.getMessage());
        }
    }

    private void enriquecerEmailsSiFaltan(Entrega entrega) {
        if ((entrega.getEmailDonante() == null || entrega.getEmailDonante().isBlank() || entrega.getIdDonante() == null)
                && entrega.getIdDonacion() != null && donacionesServiceClient != null) {
            java.util.Map<String, Object> detalle = donacionesServiceClient.obtenerDetalleDonacion(entrega.getIdDonacion());
            if (detalle != null && !detalle.isEmpty()) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> donanteMap = (java.util.Map<String, Object>) detalle.get("donante");
                if (donanteMap != null) {
                    if (entrega.getIdDonante() == null && donanteMap.get("id") instanceof Number numId) {
                        entrega.setIdDonante(numId.longValue());
                    }
                    if (donanteMap.get("emailPrincipal") instanceof String em && !em.isBlank()) {
                        entrega.setEmailDonante(em);
                    }
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> medioPred = (java.util.Map<String, Object>) donanteMap.get("medioContactoPredeterminado");
                    if (medioPred != null) {
                        if (medioPred.get("tipo") instanceof String tipoStr && !tipoStr.isBlank()) {
                            try {
                                entrega.setCanalPreferidoDonante(com.donatrack.common.enums.CanalNotificacion.valueOf(tipoStr.toUpperCase()));
                            } catch (Exception ignored) {}
                        }
                        if (medioPred.get("valor") instanceof String valStr && !valStr.isBlank()) {
                            entrega.setContactoPreferidoDonante(valStr);
                        }
                    }
                }
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> entidadMap = (java.util.Map<String, Object>) detalle.get("entidadAsignada");
                if (entidadMap != null && entidadMap.get("emailPrincipal") instanceof String em && !em.isBlank()) {
                    entrega.setEmailEntidad(em);
                }
            }
        }
    }

    @Override
    public Entrega confirmarEntregaPorDonacion(Long idDonacion, ConfirmarEntregaRequestDto request) {
        Entrega entrega = entregaRepository.findByIdDonacion(idDonacion)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada para donación: " + idDonacion));
        if (entrega.getEstado() == Entrega.EstadoEntrega.PENDIENTE) {
            entrega.marcarEnTraslado();
            entregaRepository.save(entrega);
        }
        return confirmarEntrega(entrega.getId(), request);
    }

    @Override
    public Entrega registrarEntregaFallidaPorDonacion(Long idDonacion, EntregaFallidaRequestDto request) {
        Entrega entrega = entregaRepository.findByIdDonacion(idDonacion)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada para donación: " + idDonacion));
        if (entrega.getEstado() == Entrega.EstadoEntrega.PENDIENTE) {
            entrega.marcarEnTraslado();
            entregaRepository.save(entrega);
        }
        return registrarEntregaFallida(entrega.getId(), request);
    }

    @Override
    public Entrega registrarArriboSedePorDonacion(Long idDonacion, String observaciones) {
        Entrega entrega = entregaRepository.findByIdDonacion(idDonacion)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada para donación: " + idDonacion));
        return registrarArriboSede(entrega.getId(), observaciones);
    }

    @Override
    public Entrega registrarArriboSede(String idEntrega, String observaciones) {
        Entrega entrega = obtenerPorId(idEntrega);
        enriquecerEmailsSiFaltan(entrega);
        entrega.registrarArriboSede(observaciones);
        Entrega guardada = entregaRepository.save(entrega);
        sincronizarParadaEnRuta(guardada, "EN_PUERTA_SEDE", observaciones);
        log.info("Arribo en puerta de sede registrado por chofer para entrega {} (donación {})", idEntrega, entrega.getIdDonacion());
        return guardada;
    }

    @Override
    public Entrega confirmarEntrega(String idEntrega, ConfirmarEntregaRequestDto request) {
        Entrega entrega = obtenerPorId(idEntrega);
        enriquecerEmailsSiFaltan(entrega);

        // Validación y registro en dominio (CU-43)
        entrega.confirmarRecepcion(
                request.fotos(),
                request.receptorNombre(),
                request.receptorDni(),
                request.firmaDigitalUrl()
        );
        Entrega guardada = entregaRepository.save(entrega);
        sincronizarParadaEnRuta(guardada, "ENTREGADA", null);

        // Sincronizar en donaciones-service a ENTREGADA con fotos (Entrega 3 & 4)
        if (entrega.getIdDonacion() != null) {
            String fotoUrl = (request.fotos() != null && !request.fotos().isEmpty()) ? request.fotos().get(0) : null;
            donacionesServiceClient.actualizarEstadoDonacion(
                    entrega.getIdDonacion(),
                    EstadoDonacion.ENTREGADA,
                    "Recepción confirmada con fotografías en sede por " + (request.receptorNombre() != null ? request.receptorNombre() : "entidad"),
                    "LOGISTICA_SERVICE",
                    fotoUrl
            );
        }

        // Emitir evento AMQP hacia RabbitMQ incluyendo idDonante para el cálculo de incentivos (CU-48)
        String comprobanteUrl = "http://localhost:8086/beneficiaria/notificaciones";
        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                entrega.getId(),
                entrega.getIdDonacion(),
                entrega.getIdDonante(),
                entrega.getIdEntidadBeneficiaria(),
                entrega.getPatenteCamion(),
                comprobanteUrl,
                entrega.getEmailDonante(),
                entrega.getEmailEntidad(),
                entrega.getCanalPreferidoDonante(),
                entrega.getContactoPreferidoDonante() != null ? entrega.getContactoPreferidoDonante() : entrega.getEmailDonante()
        );

        eventPublisher.publicarEntregaConfirmada(evento);
        log.info("Entrega {} confirmada con éxito.", idEntrega);

        return guardada;
    }

    @Override
    public Entrega registrarEntregaFallida(String idEntrega, EntregaFallidaRequestDto request) {
        Entrega entrega = obtenerPorId(idEntrega);
        enriquecerEmailsSiFaltan(entrega);

        // Validación estricta y registro en dominio (CU-44)
        entrega.registrarNoRecibida(request.motivo(), request.replanificable());
        Entrega guardada = entregaRepository.save(entrega);
        sincronizarParadaEnRuta(guardada, "FALLIDA", request.motivo());

        // Sincronizar en donaciones-service a ENTREGA_FALLIDA
        if (entrega.getIdDonacion() != null) {
            donacionesServiceClient.actualizarEstadoDonacion(
                    entrega.getIdDonacion(),
                    EstadoDonacion.ENTREGA_FALLIDA,
                    "Incidencia logística reportada: " + request.motivo(),
                    "CHOFER"
            );
        }

        // Emitir evento AMQP hacia RabbitMQ
        EntregaFallidaEvent evento = new EntregaFallidaEvent(
                entrega.getId(),
                entrega.getIdDonacion(),
                entrega.getIdDonante(),
                entrega.getIdEntidadBeneficiaria(),
                request.motivo(),
                request.replanificable(),
                entrega.getEmailDonante(),
                entrega.getEmailEntidad(),
                entrega.getCanalPreferidoDonante(),
                entrega.getContactoPreferidoDonante() != null ? entrega.getContactoPreferidoDonante() : entrega.getEmailDonante()
        );

        eventPublisher.publicarEntregaFallida(evento);
        log.info("Entrega {} registrada como no recibida/fallida.", idEntrega);

        return guardada;
    }

    @Override
    public Entrega replanificarEntrega(String idEntrega, ReplanificarRequestDto request) {
        Entrega entrega = obtenerPorId(idEntrega);

        // Invariante CU-45 A1: La donación debe haber regresado físicamente al depósito
        if (!request.regresoAlDeposito()) {
            throw new IllegalStateException("La entrega solo puede replanificarse si la donación regresó físicamente al depósito en buen estado (CU-45)");
        }

        entrega.replanificar(request.motivo());
        Entrega guardada = entregaRepository.save(entrega);
        sincronizarParadaEnRuta(guardada, "PENDIENTE", null);

        // Reingresar donación a EN_DEPOSITO en donaciones-service para que el matchmaking o logística la reasigne
        if (entrega.getIdDonacion() != null) {
            donacionesServiceClient.actualizarEstadoDonacion(
                    entrega.getIdDonacion(),
                    EstadoDonacion.EN_DEPOSITO,
                    "Reingreso físico al depósito tras replanificación administrativa",
                    "ADMIN"
            );
        }

        log.info("Entrega {} replanificada y reingresada a depósito exitosamente.", idEntrega);
        return guardada;
    }

    @Override
    public ComprobanteEntregaDto obtenerComprobante(String idEntrega) {
        Entrega entrega = obtenerPorId(idEntrega);
        return new ComprobanteEntregaDto(
                entrega.getId(),
                entrega.getIdDonacion(),
                entrega.getIdEntidadBeneficiaria(),
                entrega.getNombreEntidad(),
                entrega.getPatenteCamion(),
                entrega.getReceptorNombre(),
                entrega.getReceptorDni(),
                entrega.getFirmaDigitalUrl(),
                entrega.getFotosConfirmacionUrls(),
                entrega.getFechaActualizacion(),
                entrega.getEstado().name()
        );
    }

    @Override
    public List<Entrega> listarEntregasPorRuta(String idRuta) {
        return entregaRepository.findByIdRuta(idRuta);
    }

    @Override
    public Entrega obtenerPorId(String idEntrega) {
        return entregaRepository.findById(idEntrega)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada: " + idEntrega));
    }
}
