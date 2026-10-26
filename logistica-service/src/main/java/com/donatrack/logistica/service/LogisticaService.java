package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.domain.UbicacionGPS;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.EntregaFallidaRequestDto;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.dto.TelemetriaRequestDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fachada principal (Facade Pattern) del servicio de Logística.
 * Delega las operaciones en los servicios de dominio especializados respetando SRP e ISP.
 */
@Service
public class LogisticaService {

    public static final int TAMANIO_MAXIMO_LOTE = PlanificacionRutasService.TAMANIO_MAXIMO_LOTE;

    private final PlanificacionRutasService planificacionRutasService;
    private final RecorridoService recorridoService;
    private final EntregaService entregaService;
    private final TelemetriaService telemetriaService;

    public LogisticaService(PlanificacionRutasService planificacionRutasService,
                            RecorridoService recorridoService,
                            EntregaService entregaService,
                            TelemetriaService telemetriaService) {
        this.planificacionRutasService = planificacionRutasService;
        this.recorridoService = recorridoService;
        this.entregaService = entregaService;
        this.telemetriaService = telemetriaService;
    }

    public List<PlanificadorCallbackDto> planificarLotes(List<Long> donacionIds, String idCamion) {
        return planificacionRutasService.planificarLotes(donacionIds, idCamion);
    }

    public Ruta iniciarRuta(String idRuta) {
        return recorridoService.iniciarRuta(idRuta);
    }

    public Entrega confirmarEntrega(String idEntrega, List<String> fotosUrls) {
        return entregaService.confirmarEntrega(idEntrega, new ConfirmarEntregaRequestDto(fotosUrls, null, null, null));
    }

    public Entrega registrarEntregaFallida(String idEntrega, String motivoFalla, boolean replanificable) {
        return entregaService.registrarEntregaFallida(idEntrega, new EntregaFallidaRequestDto(motivoFalla, replanificable));
    }

    public UbicacionGPS registrarTelemetriaGPS(String idCamion, String patente, double latitud, double longitud, double velocidad) {
        return telemetriaService.registrarTelemetria(new TelemetriaRequestDto(
                idCamion, patente, latitud, longitud, velocidad, LocalDateTime.now(), null
        ));
    }
}
