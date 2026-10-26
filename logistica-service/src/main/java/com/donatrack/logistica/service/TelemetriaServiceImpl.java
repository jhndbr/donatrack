package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.domain.UbicacionGPS;
import com.donatrack.logistica.dto.CamionEnVivoDto;
import com.donatrack.logistica.dto.TelemetriaRequestDto;
import com.donatrack.logistica.repository.CamionRepository;
import com.donatrack.logistica.repository.RutaRepository;
import com.donatrack.logistica.repository.UbicacionGPSRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TelemetriaServiceImpl implements TelemetriaService {

    private static final Logger log = LoggerFactory.getLogger(TelemetriaServiceImpl.class);

    private final CamionRepository camionRepository;
    private final UbicacionGPSRepository gpsRepository;
    private final RutaRepository rutaRepository;

    public TelemetriaServiceImpl(CamionRepository camionRepository,
                                 UbicacionGPSRepository gpsRepository,
                                 RutaRepository rutaRepository) {
        this.camionRepository = camionRepository;
        this.gpsRepository = gpsRepository;
        this.rutaRepository = rutaRepository;
    }

    @Override
    public UbicacionGPS registrarTelemetria(TelemetriaRequestDto request) {
        String idCamion = request.idCamion();
        String patente = request.patente();

        Optional<Camion> camionOpt = camionRepository.findById(idCamion);
        if (camionOpt.isEmpty() && patente != null) {
            camionOpt = camionRepository.findByPatente(patente);
        }

        Camion camion = camionOpt.orElse(null);
        LocalDateTime ts = request.timestamp() != null ? request.timestamp() : LocalDateTime.now();

        if (camion != null) {
            boolean actualizada = camion.actualizarUbicacion(
                    request.latitud(),
                    request.longitud(),
                    request.velocidad(),
                    ts,
                    request.direccion()
            );

            if (actualizada) {
                camionRepository.save(camion);
            } else {
                log.warn("Paquete GPS descartado por desorden temporal (CU-41 A4): recibido={}, actual={}",
                        ts, camion.getUbicacionActual().getTimestamp());
            }

            if (patente == null) {
                patente = camion.getPatente();
            }
        }

        UbicacionGPS pos = new UbicacionGPS(idCamion, patente, request.latitud(), request.longitud(), request.velocidad());
        pos.setTimestamp(ts);
        return gpsRepository.save(pos);
    }

    @Override
    public Optional<UbicacionGPS> obtenerUltimaPosicion(String patente) {
        return gpsRepository.findFirstByPatenteOrderByTimestampDesc(patente);
    }

    @Override
    public List<CamionEnVivoDto> obtenerFlotaEnVivo() {
        List<Camion> camiones = camionRepository.findByActivoTrue();
        List<CamionEnVivoDto> resultado = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        for (Camion c : camiones) {
            double lat = 0.0;
            double lon = 0.0;
            double vel = 0.0;
            LocalDateTime ultTs = null;
            Long antiguedad = null;
            String direccion = null;

            if (c.getUbicacionActual() != null) {
                lat = c.getUbicacionActual().getLatitud();
                lon = c.getUbicacionActual().getLongitud();
                vel = c.getUbicacionActual().getVelocidadKmH();
                ultTs = c.getUbicacionActual().getTimestamp();
                if (ultTs != null) {
                    antiguedad = Duration.between(ultTs, ahora).toMinutes();
                }
                direccion = c.getUbicacionActual().getDireccionAproximada();
            }

            // Verificar si el camión tiene una ruta activa
            List<Ruta> rutasEnCurso = rutaRepository.findByIdCamionAndEstadoIn(
                    c.getId(), List.of(Ruta.EstadoRuta.EN_CURSO));
            String estadoRuta = rutasEnCurso.isEmpty() ? "DISPONIBLE" : "EN_RECORRIDO";

            resultado.add(new CamionEnVivoDto(
                    c.getId(),
                    c.getPatente(),
                    c.getMarcaModelo(),
                    lat,
                    lon,
                    vel,
                    ultTs,
                    antiguedad,
                    direccion,
                    c.isActivo(),
                    estadoRuta
            ));
        }

        return resultado;
    }
}
