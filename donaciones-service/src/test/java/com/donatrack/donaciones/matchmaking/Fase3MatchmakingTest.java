package com.donatrack.donaciones.matchmaking;

import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import com.donatrack.donaciones.service.MatchmakingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - Fase 3: Motor de Matchmaking Extensible (Strategy + Registry, OCP y Cálculo Dinámico)")
public class Fase3MatchmakingTest {

    @Mock
    private DonacionRepository donacionRepository;

    @Mock
    private NecesidadMaterialRepository necesidadRepository;

    @Test
    @DisplayName("Registry debe auto-registrar estrategias y permitir incorporar nuevas dinámicamente (OCP)")
    void testMatchmakingStrategyRegistryOCP() {
        CompatibilidadSemanticaStrategy semantica = new CompatibilidadSemanticaStrategy();
        PrioridadSubatendidosStrategy subatendidos = new PrioridadSubatendidosStrategy();

        MatchmakingStrategyRegistry registry = new MatchmakingStrategyRegistry(List.of(semantica, subatendidos));

        assertEquals(2, registry.listarEstrategias().size());
        assertTrue(registry.obtenerPorTipo(CompatibilidadSemanticaStrategy.class).isPresent());
        assertTrue(registry.obtenerPorTipo(PrioridadSubatendidosStrategy.class).isPresent());
        assertTrue(registry.obtenerPorNombre("compatibilidad semantica").isPresent());

        // Incorporación dinámica de una 3era estrategia sin alterar MatchmakingService
        MatchmakingStrategy cercaniaGeografica = new MatchmakingStrategy() {
            @Override
            public String getNombre() { return "Cercania Geografica"; }
            @Override
            public double calcularScore(Donacion d, NecesidadMaterial n) { return 0.92; }
        };

        registry.registrar(cercaniaGeografica);
        assertEquals(3, registry.listarEstrategias().size());
        assertTrue(registry.obtenerPorNombre("Cercania Geografica").isPresent());
    }

    @Test
    @DisplayName("CompatibilidadSemanticaStrategy debe rechazar pesos que no sumen exactamente 1.0 (CU-31)")
    void testValidacionPesosSemanticos() {
        // Suma != 1.0 lanza IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new CompatibilidadSemanticaStrategy(0.50, 0.50, 0.20) // Suma = 1.20
        );
        assertTrue(ex.getMessage().contains("debe ser exactamente 1.0"));

        // Suma == 1.0 es válida
        CompatibilidadSemanticaStrategy valida = new CompatibilidadSemanticaStrategy(0.50, 0.30, 0.20);
        assertNotNull(valida);
        assertEquals(0.50, valida.getA1());
        assertEquals(0.30, valida.getA2());
        assertEquals(0.20, valida.getA3());
    }

    @Test
    @DisplayName("PrioridadSubatendidosStrategy debe calcular dinámicamente las entregas de los últimos 90 días (CU-32)")
    void testCalculoDinamicoSubatendidos90Dias() {
        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Comedor Esperanza", "Av 1", "555-1111");
        entidad.setId(15L);
        entidad.setDonacionesRecibidasTrimestre(0); // Atributo fijo en 0

        NecesidadMaterial necesidad = new NecesidadMaterial("Fideos", "Víveres", TipoNecesidad.EXTRAORDINARIA, 50.0, "KG", null);
        necesidad.setEntidadBeneficiaria(entidad);

        Donacion donacion = new Donacion();
        donacion.setSubcategoria("Fideos");

        // Mock: en los últimos 90 días la base relacional cuenta 4 donaciones efectivamente entregadas a esa entidad
        when(donacionRepository.countEntregadasRecientesByEntidadId(eq(15L), any(LocalDateTime.class))).thenReturn(4L);

        PrioridadSubatendidosStrategy strategy = new PrioridadSubatendidosStrategy(donacionRepository, 0.1);
        double score = strategy.calcularScore(donacion, necesidad);

        // S_sub = 1 / (1 + 0.1 * 4) = 1 / 1.4 = 0.7143
        assertEquals(0.7143, score, 0.001);
        verify(donacionRepository, times(1)).countEntregadasRecientesByEntidadId(eq(15L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("MatchmakingService debe evaluar donaciones utilizando el Strategy Registry")
    void testMatchmakingServiceConRegistry() {
        CompatibilidadSemanticaStrategy semantica = new CompatibilidadSemanticaStrategy();
        PrioridadSubatendidosStrategy subatendidos = new PrioridadSubatendidosStrategy();
        MatchmakingStrategyRegistry registry = new MatchmakingStrategyRegistry(List.of(semantica, subatendidos));

        MatchmakingService service = new MatchmakingService(donacionRepository, necesidadRepository, registry);

        Donacion donacion = new Donacion();
        donacion.setId(1L);
        donacion.setSubcategoria("Lácteos");
        donacion.setCantidad(20.0);

        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Hogar Santa Ana", "Rivadavia 500", "555");
        entidad.setId(10L);
        NecesidadMaterial nec = new NecesidadMaterial("Lácteos", "Leche en polvo", TipoNecesidad.EXTRAORDINARIA, 20.0, "KG", null);
        nec.setId(100L);
        nec.setEntidadBeneficiaria(entidad);

        when(donacionRepository.findById(1L)).thenReturn(Optional.of(donacion));
        when(necesidadRepository.findPendientesPorSubcategoria("Lácteos")).thenReturn(List.of(nec));

        var ranking = service.evaluarDonacion(1L);

        assertNotNull(ranking);
        assertEquals(1, ranking.getRankingSemanticoTop10().size());
        assertEquals(1, ranking.getRankingSubatendidosTop10().size());
        assertEquals(1, ranking.getCoincidenciasDestacadas().size());

        // Evaluar dinámicamente con todas las estrategias
        var todas = service.evaluarConTodasLasEstrategias(1L);
        assertEquals(2, todas.size());
        assertTrue(todas.containsKey("Compatibilidad Semantica"));
        assertTrue(todas.containsKey("Prioridad a Subatendidos"));

        // Validar rechazo de alpha <= 0 (CORR-014)
        assertThrows(IllegalArgumentException.class, () -> subatendidos.setAlpha(0.0));
        assertThrows(IllegalArgumentException.class, () -> subatendidos.setAlpha(-0.5));
    }
}
