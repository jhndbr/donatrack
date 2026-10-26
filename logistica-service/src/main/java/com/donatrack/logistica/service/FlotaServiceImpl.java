package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.dto.CamionRequestDto;
import com.donatrack.logistica.exception.ConflictException;
import com.donatrack.logistica.exception.ResourceNotFoundException;
import com.donatrack.logistica.repository.CamionRepository;
import com.donatrack.logistica.repository.RutaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlotaServiceImpl implements FlotaService {

    private static final Logger log = LoggerFactory.getLogger(FlotaServiceImpl.class);

    private final CamionRepository camionRepository;
    private final RutaRepository rutaRepository;

    public FlotaServiceImpl(CamionRepository camionRepository, RutaRepository rutaRepository) {
        this.camionRepository = camionRepository;
        this.rutaRepository = rutaRepository;
    }

    @Override
    public List<Camion> listarCamiones(Boolean activo) {
        if (activo != null && activo) {
            return camionRepository.findByActivoTrue();
        }
        return camionRepository.findAll();
    }

    @Override
    public Camion obtenerPorId(String id) {
        return camionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camión no encontrado con id: " + id));
    }

    @Override
    public Camion obtenerPorPatente(String patente) {
        return camionRepository.findByPatente(patente)
                .orElseThrow(() -> new ResourceNotFoundException("Camión no encontrado con patente: " + patente));
    }

    @Override
    public Camion registrarCamion(CamionRequestDto dto) {
        if (camionRepository.findByPatente(dto.patente()).isPresent()) {
            throw new ConflictException("Ya existe un camión registrado con la patente: " + dto.patente());
        }

        Camion nuevo = new Camion(
                dto.patente().toUpperCase().trim(),
                dto.marcaModelo() != null ? dto.marcaModelo() : "Unidad Utilitaria",
                dto.capacidadVolumenM3(),
                dto.alturaM(),
                dto.capacidadPesoKg()
        );

        Camion guardado = camionRepository.save(nuevo);
        log.info("Camión registrado con éxito: patente={}, id={}", guardado.getPatente(), guardado.getId());
        return guardado;
    }

    @Override
    public Camion actualizarCamion(String id, CamionRequestDto dto) {
        Camion camion = obtenerPorId(id);

        // Validar invariante CU-36 A3: Si tiene rutas PLANIFICADA o EN_CURSO, no modificar capacidades
        boolean tieneRutasActivas = rutaRepository.existsByIdCamionAndEstadoIn(
                id, List.of(Ruta.EstadoRuta.PLANIFICADA, Ruta.EstadoRuta.EN_CURSO));

        if (tieneRutasActivas) {
            throw new ConflictException("No se pueden modificar las capacidades de un camión comprometido en rutas activas (PLANIFICADA o EN_CURSO)");
        }

        camion.setCapacidadVolumenM3(dto.capacidadVolumenM3());
        camion.setAlturaM(dto.alturaM());
        camion.setCapacidadPesoKg(dto.capacidadPesoKg());
        if (dto.marcaModelo() != null) {
            camion.setMarcaModelo(dto.marcaModelo());
        }

        return camionRepository.save(camion);
    }

    @Override
    public void darDeBajaCamion(String id, String motivo) {
        Camion camion = obtenerPorId(id);

        // Validar invariante CU-36 A4: Verificar que no tenga rutas pendientes
        boolean tieneRutasActivas = rutaRepository.existsByIdCamionAndEstadoIn(
                id, List.of(Ruta.EstadoRuta.PLANIFICADA, Ruta.EstadoRuta.EN_CURSO));

        if (tieneRutasActivas) {
            throw new ConflictException("No se puede dar de baja un camión asignado a rutas en estado PLANIFICADA o EN_CURSO");
        }

        camion.darDeBaja(motivo);
        camionRepository.save(camion);
        log.info("Camión dado de baja exitosamente: patente={}, motivo={}", camion.getPatente(), motivo);
    }

    @Override
    public Camion obtenerCamionDisponible() {
        return camionRepository.findByActivoTrue().stream()
                .filter(c -> !rutaRepository.existsByIdCamionAndEstadoIn(c.getId(), List.of(Ruta.EstadoRuta.EN_CURSO)))
                .findFirst()
                .orElseGet(() -> {
                    // Fallback a cualquier camión activo o inicial
                    return camionRepository.findAll().stream().findFirst().orElseGet(() -> {
                        Camion fallback = new Camion("AE789XY", 20.0, 3.0, 3500.0);
                        return camionRepository.save(fallback);
                    });
                });
    }
}
