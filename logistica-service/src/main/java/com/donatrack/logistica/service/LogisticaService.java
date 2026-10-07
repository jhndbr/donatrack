package com.donatrack.logistica.service;

import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.common.events.RutaIniciadaEvent;
import com.donatrack.logistica.domain.*;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LogisticaService {

    private static final Logger log = LoggerFactory.getLogger(LogisticaService.class);

    private final RutaRepository rutaRepository;
    private final EntregaRepository entregaRepository;
    private final CamionRepository camionRepository;
    private final UbicacionGPSRepository gpsRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${donatrack.rabbitmq.exchange:donatrack.events.exchange}")
    private String exchange;

    @Value("${donatrack.rabbitmq.routing-key.ruta-iniciada:ruta.iniciada}")
    private String routingKeyRutaIniciada;

    @Value("${donatrack.rabbitmq.routing-key.entrega-confirmada:entrega.confirmada}")
    private String routingKeyEntregaConfirmada;

    @Value("${donatrack.rabbitmq.routing-key.entrega-fallida:entrega.fallida}")
    private String routingKeyEntregaFallida;

    public LogisticaService(RutaRepository rutaRepository,
                            EntregaRepository entregaRepository,
                            CamionRepository camionRepository,
                            UbicacionGPSRepository gpsRepository,
                            RabbitTemplate rabbitTemplate) {
        this.rutaRepository = rutaRepository;
        this.entregaRepository = entregaRepository;
        this.camionRepository = camionRepository;
        this.gpsRepository = gpsRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public static final int TAMANIO_MAXIMO_LOTE = 100;

    /**
     * Planifica lotes de donaciones hacia el ruteador externo garantizando
     * lotes maximos de 100 donaciones (regla estricta del TP).
     */
    public List<PlanificadorCallbackDto> planificarLotes(List<Long> donacionIds, String idCamion) {
        if (donacionIds == null || donacionIds.isEmpty()) {
            throw new IllegalArgumentException("La lista de IDs de donaciones no puede ser vacia");
        }

        Camion camion = null;
        if (idCamion != null) {
            camion = camionRepository.findById(idCamion).orElse(null);
        }
        if (camion == null) {
            camion = camionRepository.findAll().stream().findFirst().orElseGet(() -> {
                Camion c = new Camion("AE789XY", 20.0, 3.0, 3500.0);
                return camionRepository.save(c);
            });
        }

        List<PlanificadorCallbackDto> lotesGenerados = new ArrayList<>();

        int total = donacionIds.size();
        for (int i = 0; i < total; i += TAMANIO_MAXIMO_LOTE) {
            List<Long> chunk = donacionIds.subList(i, Math.min(total, i + TAMANIO_MAXIMO_LOTE));
            String loteId = "LOTE-" + System.currentTimeMillis() + "-" + (i / TAMANIO_MAXIMO_LOTE + 1);

            PlanificadorCallbackDto dto = new PlanificadorCallbackDto();
            dto.setLoteId(loteId);

            PlanificadorCallbackDto.RutaPropuestaDto rutaProp = new PlanificadorCallbackDto.RutaPropuestaDto();
            rutaProp.setIdCamion(camion.getId());
            rutaProp.setPatenteCamion(camion.getPatente());

            List<PlanificadorCallbackDto.ParadaPropuestaDto> paradas = new ArrayList<>();
            int orden = 1;
            for (Long donacionId : chunk) {
                PlanificadorCallbackDto.ParadaPropuestaDto p = new PlanificadorCallbackDto.ParadaPropuestaDto();
                p.setOrden(orden++);
                p.setIdDonacion(donacionId);
                p.setIdEntidad(1L);
                p.setNombreEntidad("Entidad Beneficiaria Asignada");
                p.setDireccion("Av. Corrientes " + (1000 + orden * 10) + ", CABA");
                p.setLatitud(-34.6037 + (orden * 0.001));
                p.setLongitud(-58.3816 + (orden * 0.001));
                paradas.add(p);
            }
            rutaProp.setParadas(paradas);
            dto.setRutasGeneradas(List.of(rutaProp));
            dto.setDonacionesSinAsignar(List.of());

            lotesGenerados.add(dto);
            log.info("Lote {} generado con {} donaciones (maximo 100) para camion {}",
                    loteId, chunk.size(), camion.getPatente());
        }

        return lotesGenerados;
    }

    /**
     * El chofer informa en el sistema el inicio de su recorrido.
     * Las entregas pasan automaticamente al estado EN_TRASLADO y se notifica a donantes y beneficiarias.
     */
    public Ruta iniciarRuta(String idRuta) {
        Ruta ruta = rutaRepository.findById(idRuta)
                .orElseThrow(() -> new IllegalArgumentException("Ruta no encontrada: " + idRuta));

        ruta.iniciarRuta();
        Ruta guardada = rutaRepository.save(ruta);

        List<Entrega> entregas = entregaRepository.findByIdRuta(idRuta);
        List<Long> idsDonaciones = new ArrayList<>();
        List<String> emailsNotificar = new ArrayList<>();

        for (Entrega entrega : entregas) {
            entrega.marcarEnTraslado();
            entregaRepository.save(entrega);
            idsDonaciones.add(entrega.getIdDonacion());
            if (entrega.getEmailDonante() != null) emailsNotificar.add(entrega.getEmailDonante());
            if (entrega.getEmailEntidad() != null) emailsNotificar.add(entrega.getEmailEntidad());
        }

        // Emitir evento asincrono hacia RabbitMQ para notificaciones
        String linkSeguimiento = "https://donatrack.org/tracking/rutas/" + ruta.getId();
        RutaIniciadaEvent evento = new RutaIniciadaEvent(
                ruta.getId(),
                ruta.getPatenteCamion(),
                idsDonaciones,
                emailsNotificar,
                linkSeguimiento
        );

        try {
            rabbitTemplate.convertAndSend(exchange, routingKeyRutaIniciada, evento);
            log.info("Evento RutaIniciada publicado para ruta {}", ruta.getId());
        } catch (Exception e) {
            log.error("Error al publicar evento RutaIniciada: {}", e.getMessage());
        }

        return guardada;
    }

    /**
     * Confirmacion de recepcion de donacion por la entidad beneficiaria con carga de fotos.
     */
    public Entrega confirmarEntrega(String idEntrega, List<String> fotosUrls) {
        Entrega entrega = entregaRepository.findById(idEntrega)
                .orElseThrow(() -> new IllegalArgumentException("Entrega no encontrada: " + idEntrega));

        entrega.confirmarRecepcion(fotosUrls);
        Entrega guardada = entregaRepository.save(entrega);

        // Emitir evento de entrega confirmada con comprobante
        String comprobanteUrl = "https://donatrack.org/comprobantes/" + entrega.getId();
        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                entrega.getId(),
                entrega.getIdDonacion(),
                entrega.getIdEntidadBeneficiaria(),
                entrega.getPatenteCamion(),
                comprobanteUrl,
                entrega.getEmailDonante(),
                entrega.getEmailEntidad()
        );

        try {
            rabbitTemplate.convertAndSend(exchange, routingKeyEntregaConfirmada, evento);
            log.info("Evento EntregaConfirmada publicado para donacion {}", entrega.getIdDonacion());
        } catch (Exception e) {
            log.error("Error al publicar evento EntregaConfirmada: {}", e.getMessage());
        }

        return guardada;
    }

    /**
     * Registro de entrega no recibida con justificacion de falla.
     */
    public Entrega registrarEntregaFallida(String idEntrega, String motivoFalla, boolean replanificable) {
        Entrega entrega = entregaRepository.findById(idEntrega)
                .orElseThrow(() -> new IllegalArgumentException("Entrega no encontrada: " + idEntrega));

        entrega.registrarNoRecibida(motivoFalla);
        Entrega guardada = entregaRepository.save(entrega);

        EntregaFallidaEvent evento = new EntregaFallidaEvent(
                entrega.getId(),
                entrega.getIdDonacion(),
                entrega.getIdEntidadBeneficiaria(),
                motivoFalla,
                replanificable,
                entrega.getEmailDonante(),
                entrega.getEmailEntidad()
        );

        try {
            rabbitTemplate.convertAndSend(exchange, routingKeyEntregaFallida, evento);
        } catch (Exception e) {
            log.error("Error al publicar evento EntregaFallida: {}", e.getMessage());
        }

        return guardada;
    }

    /**
     * Registra punto de geolocalizacion recibido desde el GPS o la app del chofer.
     */
    public UbicacionGPS registrarTelemetriaGPS(String idCamion, String patente, double latitud, double longitud, double velocidad) {
        UbicacionGPS pos = new UbicacionGPS(idCamion, patente, latitud, longitud, velocidad);
        return gpsRepository.save(pos);
    }
}
