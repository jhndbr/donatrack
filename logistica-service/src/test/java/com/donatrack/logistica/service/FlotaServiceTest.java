package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.dto.CamionRequestDto;
import com.donatrack.logistica.exception.ConflictException;
import com.donatrack.logistica.repository.CamionRepository;
import com.donatrack.logistica.repository.RutaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FlotaServiceTest {

    @Mock
    private CamionRepository camionRepository;

    @Mock
    private RutaRepository rutaRepository;

    @InjectMocks
    private FlotaServiceImpl flotaService;

    private Camion camion;

    @BeforeEach
    void setUp() {
        camion = new Camion("AF123CD", "Mercedes Accelo", 18.0, 3.0, 3500.0);
        camion.setId("CAM-01");
    }

    @Test
    void testRegistrarCamionPatenteDuplicadaLanzaConflictException() {
        when(camionRepository.findByPatente("AF123CD")).thenReturn(Optional.of(camion));

        CamionRequestDto dto = new CamionRequestDto("AF123CD", "Mercedes", 18.0, 3.0, 3500.0);
        assertThrows(ConflictException.class, () -> flotaService.registrarCamion(dto));
    }

    @Test
    void testModificarCamionConRutasActivasLanzaConflictException() {
        when(camionRepository.findById("CAM-01")).thenReturn(Optional.of(camion));
        when(rutaRepository.existsByIdCamionAndEstadoIn(eq("CAM-01"), anyList())).thenReturn(true);

        CamionRequestDto dto = new CamionRequestDto("AF123CD", "Mercedes Actualizado", 22.0, 3.2, 4000.0);
        assertThrows(ConflictException.class, () -> flotaService.actualizarCamion("CAM-01", dto));
    }

    @Test
    void testDarDeBajaCamionConRutasActivasLanzaConflictException() {
        when(camionRepository.findById("CAM-01")).thenReturn(Optional.of(camion));
        when(rutaRepository.existsByIdCamionAndEstadoIn(eq("CAM-01"), anyList())).thenReturn(true);

        assertThrows(ConflictException.class, () -> flotaService.darDeBajaCamion("CAM-01", "Mantenimiento preventivo"));
    }

    @Test
    void testDarDeBajaCamionSinRutasActivasExitoso() {
        when(camionRepository.findById("CAM-01")).thenReturn(Optional.of(camion));
        when(rutaRepository.existsByIdCamionAndEstadoIn(eq("CAM-01"), anyList())).thenReturn(false);

        flotaService.darDeBajaCamion("CAM-01", "Mantenimiento general");

        assertFalse(camion.isActivo());
        assertEquals("Mantenimiento general", camion.getMotivoBaja());
        assertNotNull(camion.getFechaBaja());
        verify(camionRepository, times(1)).save(camion);
    }
}
