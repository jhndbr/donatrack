package com.donatrack.donaciones.service;

import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.matchmaking.CompatibilidadSemanticaStrategy;
import com.donatrack.donaciones.matchmaking.MatchmakingRankingResult;
import com.donatrack.donaciones.matchmaking.PrioridadSubatendidosStrategy;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MatchmakingServiceTest {

    @Mock
    private DonacionRepository donacionRepository;

    @Mock
    private NecesidadMaterialRepository necesidadRepository;

    @Mock
    private CompatibilidadSemanticaStrategy estrategiaSemantica;

    @Mock
    private PrioridadSubatendidosStrategy estrategiaSubatendidos;

    @InjectMocks
    private MatchmakingService matchmakingService;

    private Donacion donacion;
    private NecesidadMaterial necesidad1;
    private NecesidadMaterial necesidad2;

    @BeforeEach
    void setUp() {
        PersonaHumana donante = new PersonaHumana("Juan", "Perez", 30, "30111222", "M", "Calle 1");
        donacion = new Donacion(donante, "Alimentos", 50.0, "kg", false, null);
        donacion.setId(1L);

        EntidadBeneficiaria entidadA = new EntidadBeneficiaria("Comedor A", "Av 1", "123");
        entidadA.setId(10L);
        necesidad1 = new NecesidadMaterial("Alimentos", "Arroz y fideos", TipoNecesidad.EXTRAORDINARIA, 50.0, "kg", null);
        necesidad1.setId(101L);
        necesidad1.setEntidadBeneficiaria(entidadA);

        EntidadBeneficiaria entidadB = new EntidadBeneficiaria("Comedor B", "Av 2", "456");
        entidadB.setId(20L);
        necesidad2 = new NecesidadMaterial("Alimentos", "Harina", TipoNecesidad.RECURRENTE, 20.0, "kg", "MENSUAL");
        necesidad2.setId(102L);
        necesidad2.setEntidadBeneficiaria(entidadB);
    }

    @Test
    void testEvaluarDonacionExitosaConCoincidencias() {
        when(donacionRepository.findById(1L)).thenReturn(Optional.of(donacion));
        when(necesidadRepository.findPendientesPorSubcategoria("Alimentos")).thenReturn(List.of(necesidad1, necesidad2));

        when(estrategiaSemantica.calcularScore(eq(donacion), eq(necesidad1))).thenReturn(0.95);
        when(estrategiaSemantica.calcularScore(eq(donacion), eq(necesidad2))).thenReturn(0.60);

        when(estrategiaSubatendidos.calcularScore(eq(donacion), eq(necesidad1))).thenReturn(0.85);
        when(estrategiaSubatendidos.calcularScore(eq(donacion), eq(necesidad2))).thenReturn(0.90);

        MatchmakingRankingResult result = matchmakingService.evaluarDonacion(1L);

        assertNotNull(result);
        assertEquals(1L, result.getIdDonacion());
        assertEquals("Alimentos", result.getSubcategoria());
        assertEquals(2, result.getRankingSemanticoTop10().size());
        assertEquals(2, result.getRankingSubatendidosTop10().size());

        // El primer lugar en ranking semántico debe ser necesidad1 (0.95)
        assertEquals(101L, result.getRankingSemanticoTop10().get(0).getIdNecesidad());
        assertEquals(0.95, result.getRankingSemanticoTop10().get(0).getScore());

        // El primer lugar en ranking subatendidos debe ser necesidad2 (0.90)
        assertEquals(102L, result.getRankingSubatendidosTop10().get(0).getIdNecesidad());
        assertEquals(0.90, result.getRankingSubatendidosTop10().get(0).getScore());

        // Ambas necesidades están en ambos tops, por ende hay 2 coincidencias destacadas
        assertEquals(2, result.getCoincidenciasDestacadas().size());
    }

    @Test
    void testEvaluarDonacionInexistenteLanzaExcepcion() {
        when(donacionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> matchmakingService.evaluarDonacion(999L));
        verify(necesidadRepository, never()).findPendientesPorSubcategoria(any());
    }

    @Test
    void testEvaluarDonacionSinNecesidadesPendientes() {
        when(donacionRepository.findById(1L)).thenReturn(Optional.of(donacion));
        when(necesidadRepository.findPendientesPorSubcategoria("Alimentos")).thenReturn(Collections.emptyList());

        MatchmakingRankingResult result = matchmakingService.evaluarDonacion(1L);

        assertNotNull(result);
        assertTrue(result.getRankingSemanticoTop10().isEmpty());
        assertTrue(result.getRankingSubatendidosTop10().isEmpty());
        assertTrue(result.getCoincidenciasDestacadas().isEmpty());
    }
}
