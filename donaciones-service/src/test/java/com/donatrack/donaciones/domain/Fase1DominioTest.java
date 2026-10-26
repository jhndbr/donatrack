package com.donatrack.donaciones.domain;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.enums.TipoNecesidad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - Fase 1: Dominio, Concurrencia y Auditoría")
public class Fase1DominioTest {

    @Test
    @DisplayName("Debe registrar y desasignar donación revirtiendo la cobertura en la necesidad material (CU-18, CU-34)")
    void testAsignacionYDesasignacionAdministrativa() {
        PersonaHumana donante = new PersonaHumana("Carlos", "Gómez", 42, "30111222", "M", "San Martín 123");
        Donacion donacion = new Donacion(donante, "Fideos Secos", 50.0, "KG", false, LocalDate.now().plusMonths(6));

        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Comedor Infantil Los Bajitos", "Calle 10 Nro 450", "11-4455-6677");
        NecesidadMaterial necesidad = new NecesidadMaterial("Fideos Secos", "Alimento no perecedero",
                TipoNecesidad.EXTRAORDINARIA, 100.0, "KG", null);
        entidad.agregarNecesidad(necesidad);

        // 1. Asignar formalmente vinculando necesidad y registrando operador
        donacion.asignarA(entidad, necesidad, "OPERADOR_JUAN");

        assertEquals(EstadoDonacion.ASIGNACION_REALIZADA, donacion.getEstado());
        assertEquals(entidad, donacion.getEntidadAsignada());
        assertEquals(necesidad, donacion.getNecesidadAsignada());
        assertEquals(50.0, necesidad.getCantidadCubierta());
        assertFalse(necesidad.estaSatisfecha());

        // Verificar trazabilidad en historial
        List<HistorialEstadoDonacion> historial = donacion.getHistorialEstados();
        assertTrue(historial.size() >= 2);
        HistorialEstadoDonacion ultimo = historial.get(historial.size() - 1);
        assertEquals(EstadoDonacion.ASIGNACION_REALIZADA, ultimo.getNuevoEstado());
        assertEquals("OPERADOR_JUAN", ultimo.getUsuarioResponsable());

        // 2. Desasignar con motivo justificado (CU-18)
        donacion.desasignar("Reasignación prioritaria por emergencia hídrica", "ADMIN_ANA");

        assertEquals(EstadoDonacion.EN_DEPOSITO, donacion.getEstado());
        assertNull(donacion.getEntidadAsignada());
        assertNull(donacion.getNecesidadAsignada());
        assertEquals(0.0, necesidad.getCantidadCubierta(), "La cantidad cubierta en la necesidad debe revertirse");

        HistorialEstadoDonacion auditDesasignacion = donacion.getHistorialEstados().get(donacion.getHistorialEstados().size() - 1);
        assertEquals(EstadoDonacion.EN_DEPOSITO, auditDesasignacion.getNuevoEstado());
        assertEquals("ADMIN_ANA", auditDesasignacion.getUsuarioResponsable());
        assertTrue(auditDesasignacion.getJustificacion().contains("Reasignación prioritaria"));
    }

    @Test
    @DisplayName("Debe rechazar asignación si la subcategoría de la necesidad no coincide exactamente (Unidad Mínima)")
    void testRechazarAsignacionSubcategoriaDiscrepante() {
        PersonaHumana donante = new PersonaHumana("Lucía", "Pérez", 29, "35999888", "F", "Belgrano 456");
        Donacion donacion = new Donacion(donante, "Leche Larga Vida", 30.0, "L", false, LocalDate.now().plusMonths(3));

        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Hogar Santa Rita", "Mitre 800", "11-2233-4455");
        NecesidadMaterial necesidad = new NecesidadMaterial("Arroz Blanco", "Víveres",
                TipoNecesidad.RECURRENTE, 50.0, "KG", "MENSUAL");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                donacion.asignarA(entidad, necesidad, "OPERADOR_TEST")
        );
        assertTrue(ex.getMessage().contains("no coincide con la subcategoría de la donación"));
    }

    @Test
    @DisplayName("Debe gestionar baja lógica y reactivación en Donante y Entidad Beneficiaria (CU-11, CU-25)")
    void testBajaLogicaYReactivacionActores() {
        PersonaHumana donante = new PersonaHumana("Marcos", "Díaz", 35, "28777666", "M", "Rivadavia 100");
        assertTrue(donante.isActivo());
        assertNull(donante.getFechaBaja());

        // Baja lógica
        donante.darDeBaja("Solicitud voluntaria del titular");
        assertFalse(donante.isActivo());
        assertNotNull(donante.getFechaBaja());
        assertEquals("Solicitud voluntaria del titular", donante.getMotivoBaja());

        // Reactivación
        donante.reactivar();
        assertTrue(donante.isActivo());
        assertNull(donante.getFechaBaja());
        assertNull(donante.getMotivoBaja());

        // Entidad Beneficiaria
        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Comedor Esperanza", "Av 9 de Julio 1200", "11-9988-7766");
        assertTrue(entidad.isActivo());

        entidad.darDeBaja("Cese temporal de actividades");
        assertFalse(entidad.isActivo());
        assertEquals("Cese temporal de actividades", entidad.getMotivoBaja());

        entidad.reactivar();
        assertTrue(entidad.isActivo());
        assertNull(entidad.getMotivoBaja());
    }

    @Test
    @DisplayName("Debe validar consistencia de bienes rechazando cantidades negativas o fechas pasadas (CU-14)")
    void testValidacionConsistenciaBienes() {
        // Cantidad <= 0
        assertThrows(IllegalArgumentException.class, () ->
                new BienDonado("Fideos", "Alimentos", "Pastas", 0.0, "KG", false, null, null)
        );

        // Fecha de vencimiento pasada para perecedero
        assertThrows(IllegalArgumentException.class, () ->
                new BienDonado("Yogur", "Alimentos", "Lácteos", 10.0, "L", false, LocalDate.now().minusDays(1), null)
        );

        // Bien válido con fecha futura
        BienDonado bienValido = new BienDonado("Arroz", "Alimentos", "Granos", 25.0, "KG", false, LocalDate.now().plusYears(1), null);
        assertNotNull(bienValido);
        assertEquals(25.0, bienValido.getCantidad());
        assertFalse(bienValido.estaVencido());
    }

    @Test
    @DisplayName("Debe validar método esEditable() solo en EN_DEPOSITO (CU-18)")
    void testEsEditableSoloEnDeposito() {
        Donacion donacion = new Donacion();
        assertTrue(donacion.esEditable(), "Debe ser editable cuando está EN_DEPOSITO");

        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Entidad Test", "Dir Test", "Tel Test");
        donacion.asignarA(entidad);
        assertFalse(donacion.esEditable(), "No debe ser editable una vez en ASIGNACION_REALIZADA");

        donacion.marcarListaParaEntregar();
        assertFalse(donacion.esEditable(), "No debe ser editable cuando está LISTA_PARA_ENTREGAR");
    }

    @Test
    @DisplayName("Debe verificar presencia de atributo version para bloqueo optimista (FT-09)")
    void testVersionControlConcurrenciaOptimista() {
        Donacion donacion = new Donacion();
        assertNull(donacion.getVersion());
        donacion.setVersion(1L);
        assertEquals(1L, donacion.getVersion());
    }
}
