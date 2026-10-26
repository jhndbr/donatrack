package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.domain.CoordenadaGPS;
import com.donatrack.logistica.domain.UbicacionGPS;
import com.donatrack.logistica.dto.CamionEnVivoDto;
import com.donatrack.logistica.dto.TelemetriaRequestDto;
import com.donatrack.logistica.repository.CamionRepository;
import com.donatrack.logistica.repository.RutaRepository;
import com.donatrack.logistica.repository.UbicacionGPSRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelemetriaServiceTest {

    @Mock
    private CamionRepository camionRepository;

    @Mock
    private UbicacionGPSRepository gpsRepository;

    @Mock
    private RutaRepository rutaRepository;

    @InjectMocks
    private TelemetriaServiceImpl telemetriaService;

    private Camion camion;

    @BeforeEach
    void setUp() {
        camion = new Camion("AF123CD", "Mercedes", 18.0, 3.0, 3500.0);
        camion.setId("CAM-01");
        camion.setUbicacionActual(new CoordenadaGPS(-34.6037, -58.3816, 40.0, LocalDateTime.now(), "Obelisco"));
    }

    @Test
    void testDescartePaqueteGPSDesordenadoTemporalmente() {
        when(camionRepository.findById("CAM-01")).thenReturn(Optional.of(camion));
        when(gpsRepository.save(any(UbicacionGPS.class))).thenAnswer(inv -> inv.getArgument(0));

        // Paquete con timestamp anterior al actual (desorden en la red móvil - CU-41 A4)
        LocalDateTime timestampViejo = LocalDateTime.now().minusMinutes(10);
        TelemetriaRequestDto dto = new TelemetriaRequestDto(
                "CAM-01", "AF123CD", -34.7000, -58.4000, 50.0, timestampViejo, "Posición Vieja"
        );

        telemetriaService.registrarTelemetria(dto);

        // La posición del camión no debió ser sobreescrita por la vieja
        assertEquals(-34.6037, camion.getUbicacionActual().getLatitud());
        assertEquals("Obelisco", camion.getUbicacionActual().getDireccionAproximada());
        verify(camionRepository, never()).save(camion);
    }

    @Test
    void testFlotaEnVivoCalculaAntiguedadYEstadoRuta() {
        when(camionRepository.findByActivoTrue()).thenReturn(List.of(camion));
        when(rutaRepository.findByIdCamionAndEstadoIn(eq("CAM-01"), anyList())).thenReturn(List.of());

        List<CamionEnVivoDto> flota = telemetriaService.obtenerFlotaEnVivo();

        assertEquals(1, flota.size());
        assertEquals("AF123CD", flota.get(0).patente());
        assertEquals("DISPONIBLE", flota.get(0).estadoRuta());
        assertNotNull(flota.get(0).antiguedadMinutos());
    }
}
