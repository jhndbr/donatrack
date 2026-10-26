package com.donatrack.logistica.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.RutaIniciadaEvent;
import com.donatrack.logistica.client.DonacionesServiceClient;
import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.exception.ResourceNotFoundException;
import com.donatrack.logistica.publisher.LogisticaEventPublisher;
import com.donatrack.logistica.repository.EntregaRepository;
import com.donatrack.logistica.repository.RutaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecorridoServiceImpl implements RecorridoService {

    private static final Logger log = LoggerFactory.getLogger(RecorridoServiceImpl.class);

    private final RutaRepository rutaRepository;
    private final EntregaRepository entregaRepository;
    private final DonacionesServiceClient donacionesServiceClient;
    private final LogisticaEventPublisher eventPublisher;

    public RecorridoServiceImpl(RutaRepository rutaRepository,
                                EntregaRepository entregaRepository,
                                DonacionesServiceClient donacionesServiceClient,
                                LogisticaEventPublisher eventPublisher) {
        this.rutaRepository = rutaRepository;
        this.entregaRepository = entregaRepository;
        this.donacionesServiceClient = donacionesServiceClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Ruta iniciarRuta(String idRuta) {
        Ruta ruta = rutaRepository.findById(idRuta)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada: " + idRuta));

        ruta.iniciarRuta();
        Ruta guardada = rutaRepository.save(ruta);

        List<Entrega> entregas = entregaRepository.findByIdRuta(idRuta);
        List<Long> idsDonaciones = new ArrayList<>();
        List<String> emailsNotificar = new ArrayList<>();

        for (Entrega entrega : entregas) {
            if ((entrega.getEmailDonante() == null || entrega.getEmailDonante().isBlank())
                    && entrega.getIdDonacion() != null && donacionesServiceClient != null) {
                java.util.Map<String, Object> detalle = donacionesServiceClient.obtenerDetalleDonacion(entrega.getIdDonacion());
                if (detalle != null && !detalle.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> donanteMap = (java.util.Map<String, Object>) detalle.get("donante");
                    if (donanteMap != null && donanteMap.get("emailPrincipal") instanceof String em && !em.isBlank()) {
                        entrega.setEmailDonante(em);
                    }
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> entidadMap = (java.util.Map<String, Object>) detalle.get("entidadAsignada");
                    if (entidadMap != null && entidadMap.get("emailPrincipal") instanceof String em && !em.isBlank()) {
                        entrega.setEmailEntidad(em);
                    }
                }
            }
            entrega.marcarEnTraslado();
            entregaRepository.save(entrega);
            if (entrega.getIdDonacion() != null) {
                idsDonaciones.add(entrega.getIdDonacion());
            }
            if (entrega.getEmailDonante() != null && !entrega.getEmailDonante().isBlank()) {
                emailsNotificar.add(entrega.getEmailDonante());
            }
            if (entrega.getEmailEntidad() != null && !entrega.getEmailEntidad().isBlank()) {
                emailsNotificar.add(entrega.getEmailEntidad());
            }
        }

        // Sincronizar en donaciones-service a EN_TRASLADO (CU-40)
        if (!idsDonaciones.isEmpty()) {
            donacionesServiceClient.actualizarEstadoLote(
                    idsDonaciones,
                    EstadoDonacion.EN_TRASLADO,
                    "Ruta iniciada por chofer en camión " + ruta.getPatenteCamion()
            );
        }

        // Emitir evento asíncrono hacia RabbitMQ con link de mapa interactivo
        String linkSeguimiento = "http://localhost:8086/donante/tracking";
        RutaIniciadaEvent evento = new RutaIniciadaEvent(
                ruta.getId(),
                ruta.getPatenteCamion(),
                idsDonaciones,
                emailsNotificar,
                linkSeguimiento
        );

        eventPublisher.publicarRutaIniciada(evento);
        log.info("Recorrido iniciado con éxito para la ruta {}", ruta.getId());

        return guardada;
    }

    @Override
    public Ruta finalizarRuta(String idRuta) {
        Ruta ruta = rutaRepository.findById(idRuta)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada: " + idRuta));

        ruta.finalizarRuta();
        Ruta guardada = rutaRepository.save(ruta);
        log.info("Ruta {} finalizada exitosamente.", idRuta);
        return guardada;
    }

    @Override
    public Ruta obtenerRutaPorId(String idRuta) {
        return rutaRepository.findById(idRuta)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada: " + idRuta));
    }

    @Override
    public List<Ruta> listarRutas(LocalDate fecha, Ruta.EstadoRuta estado) {
        if (fecha != null && estado != null) {
            return rutaRepository.findByFechaPlanificada(fecha).stream()
                    .filter(r -> r.getEstado() == estado)
                    .toList();
        } else if (fecha != null) {
            return rutaRepository.findByFechaPlanificada(fecha);
        } else if (estado != null) {
            return rutaRepository.findByEstado(estado);
        }
        return rutaRepository.findAll();
    }
}
