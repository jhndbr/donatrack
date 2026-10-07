package com.donatrack.donaciones.domain;

import com.donatrack.common.enums.EstadoDonacion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DonacionStateTest {

    @Test
    void testFlujoExitosoCompleto() {
        Donacion donacion = new Donacion();
        assertEquals(EstadoDonacion.EN_DEPOSITO, donacion.getEstado());

        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Comedor Sonrisas", "Av 123", "555-1111");
        donacion.asignarA(entidad);
        assertEquals(EstadoDonacion.ASIGNACION_REALIZADA, donacion.getEstado());
        assertEquals(entidad, donacion.getEntidadAsignada());

        donacion.marcarListaParaEntregar();
        assertEquals(EstadoDonacion.LISTA_PARA_ENTREGAR, donacion.getEstado());

        donacion.iniciarTraslado();
        assertEquals(EstadoDonacion.EN_TRASLADO, donacion.getEstado());

        donacion.confirmarEntrega();
        assertEquals(EstadoDonacion.ENTREGADA, donacion.getEstado());
        assertEquals(1, entidad.getDonacionesRecibidasTrimestre());
    }

    @Test
    void testEntregaFallidaRequiereJustificacion() {
        Donacion donacion = new Donacion();
        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Hogar Niños", "Calle 5", "555-2222");
        donacion.asignarA(entidad);
        donacion.marcarListaParaEntregar();
        donacion.iniciarTraslado();

        // Justificacion vacia debe lanzar excepcion segun requerimientos
        assertThrows(IllegalArgumentException.class, () -> donacion.registrarEntregaFallida(""));
        assertThrows(IllegalArgumentException.class, () -> donacion.registrarEntregaFallida(null));

        donacion.registrarEntregaFallida("Tocamos timbre pero nadie respondió");
        assertEquals(EstadoDonacion.ENTREGA_FALLIDA, donacion.getEstado());
        assertEquals("Tocamos timbre pero nadie respondió", donacion.getJustificacionFalla());

        // Reingreso a deposito
        donacion.reingresarADeposito();
        assertEquals(EstadoDonacion.EN_DEPOSITO, donacion.getEstado());
        assertNull(donacion.getEntidadAsignada());
    }

    @Test
    void testTransicionInvalidaLanzaExcepcion() {
        Donacion donacion = new Donacion();
        // No se puede confirmar entrega directamente desde EN_DEPOSITO
        assertThrows(IllegalStateException.class, donacion::confirmarEntrega);
    }
}
