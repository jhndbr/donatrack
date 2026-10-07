package com.donatrack.donaciones.matchmaking;

import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MatchmakingStrategyTest {

    private CompatibilidadSemanticaStrategy semanticaStrategy;
    private PrioridadSubatendidosStrategy subatendidosStrategy;

    @BeforeEach
    void setUp() {
        semanticaStrategy = new CompatibilidadSemanticaStrategy();
        subatendidosStrategy = new PrioridadSubatendidosStrategy();
    }

    @Test
    void testCompatibilidadSemanticaExacta() {
        Donacion donacion = new Donacion();
        donacion.setSubcategoria("fideos secos");
        donacion.setCantidad(50);

        NecesidadMaterial necesidad = new NecesidadMaterial(
                "fideos secos",
                "fideos secos",
                TipoNecesidad.EXTRAORDINARIA,
                50,
                "unidades",
                null
        );

        double score = semanticaStrategy.calcularScore(donacion, necesidad);
        // Texto identico (chi1 = 1.0 -> 0.40), volumen exacto (chi2 = 1.0 -> 0.40), extraordinaria (chi3 = 1.0 -> 0.20)
        // Score esperado = 0.40 + 0.40 + 0.20 = 1.0
        assertEquals(1.0, score, 0.001);
    }

    @Test
    void testCompatibilidadSemanticaRecurrenteYDesperdicio() {
        Donacion donacion = new Donacion();
        donacion.setSubcategoria("arroz");
        donacion.setCantidad(200); // 200 donadas para 100 requeridas -> R = 2.0 -> chi2 = 0.5

        NecesidadMaterial necesidad = new NecesidadMaterial(
                "arroz",
                "arroz",
                TipoNecesidad.RECURRENTE,
                100,
                "kg",
                "MENSUAL"
        );

        double score = semanticaStrategy.calcularScore(donacion, necesidad);
        // chi1 = 1.0 (0.40), chi2 = 0.5 (0.40 * 0.5 = 0.20), chi3 = 0.5 (0.20 * 0.5 = 0.10)
        // Score esperado = 0.40 + 0.20 + 0.10 = 0.70
        assertEquals(0.70, score, 0.001);
    }

    @Test
    void testPrioridadSubatendidosSuavizado() {
        Donacion donacion = new Donacion();

        EntidadBeneficiaria entidadCero = new EntidadBeneficiaria("Escuela 1", "Calle 1", "123");
        entidadCero.setDonacionesRecibidasTrimestre(0);
        NecesidadMaterial necCero = new NecesidadMaterial();
        necCero.setEntidadBeneficiaria(entidadCero);

        EntidadBeneficiaria entidadDiez = new EntidadBeneficiaria("Escuela 2", "Calle 2", "456");
        entidadDiez.setDonacionesRecibidasTrimestre(10);
        NecesidadMaterial necDiez = new NecesidadMaterial();
        necDiez.setEntidadBeneficiaria(entidadDiez);

        double scoreCero = subatendidosStrategy.calcularScore(donacion, necCero);
        double scoreDiez = subatendidosStrategy.calcularScore(donacion, necDiez);

        // Score 0 donaciones = 1 / (1 + 0.1 * 0) = 1.0
        assertEquals(1.0, scoreCero, 0.001);

        // Score 10 donaciones = 1 / (1 + 0.1 * 10) = 1 / 2 = 0.5
        assertEquals(0.5, scoreDiez, 0.001);

        assertTrue(scoreCero > scoreDiez);
    }
}
