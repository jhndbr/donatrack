package com.donatrack.donaciones.service;

import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.NecesidadMaterial;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.repository.EntidadBeneficiariaRepository;
import com.donatrack.donaciones.repository.NecesidadMaterialRepository;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - Fase 2: Servicios, Reglas de Negocio y Bajas Lógicas")
public class Fase2ServiciosTest {

    @Mock
    private PersonaDonanteRepository donanteRepository;

    @Mock
    private EntidadBeneficiariaRepository entidadRepository;

    @Mock
    private NecesidadMaterialRepository necesidadRepository;

    @Mock
    private DonacionRepository donacionRepository;

    @Mock
    private NormalizacionDireccionService normalizacionService;

    @InjectMocks
    private DonanteService donanteService;

    @InjectMocks
    private EntidadBeneficiariaService entidadService;

    private PersonaHumana donante;
    private EntidadBeneficiaria entidad;

    @BeforeEach
    void setUp() {
        donante = new PersonaHumana("Roberto", "Silva", 40, "20333444", "M", "Av. Corrientes 1000");
        donante.setId(5L);

        entidad = new EntidadBeneficiaria("Hogar Belén", "Calle Florida 200", "11-8888-9999");
        entidad.setId(20L);
    }

    @Test
    @DisplayName("Baja lógica de donante debe abortar si posee donaciones activas asociadas (CU-11)")
    void testBajaLogicaDonanteConDonacionesActivasBloqueada() {
        when(donanteRepository.findById(5L)).thenReturn(Optional.of(donante));
        when(donacionRepository.countActivasByDonanteId(5L)).thenReturn(2L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                donanteService.darDeBajaLogica(5L, "Intento de baja con donaciones")
        );

        assertTrue(ex.getMessage().contains("posee 2 donación(es) activa(s)"));
        verify(donanteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Baja lógica de donante debe completarse exitosamente si no posee donaciones activas (CU-11)")
    void testBajaLogicaDonanteSinDonacionesActivasExitosa() {
        when(donanteRepository.findById(5L)).thenReturn(Optional.of(donante));
        when(donacionRepository.countActivasByDonanteId(5L)).thenReturn(0L);
        when(donanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var donanteInactivado = donanteService.darDeBajaLogica(5L, "Solicitud del usuario");

        assertFalse(donanteInactivado.isActivo());
        assertEquals("Solicitud del usuario", donanteInactivado.getMotivoBaja());
        verify(donanteRepository, times(1)).save(donante);
    }

    @Test
    @DisplayName("Reactivación de donante debe restaurar estado activo y limpiar motivos (CU-11)")
    void testReactivarDonante() {
        donante.darDeBaja("Baja previa");
        when(donanteRepository.findById(5L)).thenReturn(Optional.of(donante));
        when(donanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var donanteReactivado = donanteService.reactivarDonante(5L);

        assertTrue(donanteReactivado.isActivo());
        assertNull(donanteReactivado.getFechaBaja());
        assertNull(donanteReactivado.getMotivoBaja());
    }

    @Test
    @DisplayName("Baja lógica de entidad debe abortar si posee donaciones activas asignadas o en traslado (CU-25)")
    void testBajaLogicaEntidadConDonacionesActivasBloqueada() {
        when(entidadRepository.findById(20L)).thenReturn(Optional.of(entidad));
        when(donacionRepository.countActivasByEntidadId(20L)).thenReturn(1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                entidadService.darDeBajaLogica(20L, "Cierre temporal")
        );

        assertTrue(ex.getMessage().contains("posee 1 donación(es) asignadas o en traslado"));
        verify(entidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("Baja lógica de entidad debe completarse si no posee donaciones pendientes (CU-25)")
    void testBajaLogicaEntidadSinDonacionesActivasExitosa() {
        when(entidadRepository.findById(20L)).thenReturn(Optional.of(entidad));
        when(donacionRepository.countActivasByEntidadId(20L)).thenReturn(0L);
        when(entidadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var entidadInactivada = entidadService.darDeBajaLogica(20L, "Trámite legal finalizado");

        assertFalse(entidadInactivada.isActivo());
        assertEquals("Trámite legal finalizado", entidadInactivada.getMotivoBaja());
    }

    @Test
    @DisplayName("Registro de necesidad RECURRENTE debe exigir período semanal o mensual (CU-26)")
    void testRegistrarNecesidadRecurrenteRequierePeriodo() {
        when(entidadRepository.findById(20L)).thenReturn(Optional.of(entidad));

        NecesidadMaterial necSinPeriodo = new NecesidadMaterial("Leche", "Desayunos", TipoNecesidad.RECURRENTE, 100.0, "L", null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                entidadService.registrarNecesidad(20L, necSinPeriodo)
        );

        assertTrue(ex.getMessage().contains("obligatorio especificar el período"));
    }

    @Test
    @DisplayName("Modificación de necesidad no puede reducir cantidad por debajo de lo ya cubierto (CU-27)")
    void testModificarNecesidadRechazaCantidadInferiorACubierta() {
        NecesidadMaterial nec = new NecesidadMaterial("Harina", "Comedor", TipoNecesidad.EXTRAORDINARIA, 50.0, "KG", null);
        nec.registrarCobertura(30.0); // 30 cubiertos

        when(necesidadRepository.findById(100L)).thenReturn(Optional.of(nec));

        // Intento de bajar a 20 cuando ya tiene 30 cubiertos
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                entidadService.modificarNecesidad(100L, 20.0, "Ajuste a la baja erróneo")
        );

        assertTrue(ex.getMessage().contains("no puede ser inferior a la cantidad ya cubierta"));
    }

    @Test
    @DisplayName("Cancelación formal de necesidad debe registrar motivo y desactivar la necesidad (CU-29)")
    void testCancelarNecesidadFormalmente() {
        NecesidadMaterial nec = new NecesidadMaterial("Frazadas", "Invierno", TipoNecesidad.EXTRAORDINARIA, 40.0, "unidades", null);
        when(necesidadRepository.findById(100L)).thenReturn(Optional.of(nec));
        when(necesidadRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var cancelada = entidadService.cancelarNecesidad(100L, "Donación directa recibida por otra vía");

        assertFalse(cancelada.isActiva());
        assertEquals("Donación directa recibida por otra vía", cancelada.getMotivoCierre());
        assertNotNull(cancelada.getFechaCierre());
    }

    @Test
    @DisplayName("Validaciones de alta y actualización de PersonaHumana y PersonaJuridica (CORR-005)")
    void testValidacionesYActualizacionDonantes() {
        com.donatrack.donaciones.domain.PersonaJuridica jurSinContacto =
                new com.donatrack.donaciones.domain.PersonaJuridica("Empresa SA", "30-11223344-5", "Empresa", "Alimentos");
        assertThrows(IllegalArgumentException.class, () -> donanteService.registrarPersonaJuridica(jurSinContacto));

        com.donatrack.donaciones.domain.PersonaJuridica jurSinRazon =
                new com.donatrack.donaciones.domain.PersonaJuridica("", "30-11223344-5", "Empresa", "Alimentos");
        jurSinRazon.agregarMedioContacto(new com.donatrack.donaciones.domain.MedioContacto(
                com.donatrack.common.enums.CanalNotificacion.EMAIL, "emp@mail.com", true));
        assertThrows(IllegalArgumentException.class, () -> donanteService.registrarPersonaJuridica(jurSinRazon));

        // Actualización de PersonaHumana
        when(donanteRepository.findById(5L)).thenReturn(Optional.of(donante));
        when(donanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PersonaHumana cambios = new PersonaHumana("Roberto Carlos", "Silva", 41, "20333444", "M", "Sin direccion");
        cambios.setTipoDocumento("PASAPORTE");
        PersonaHumana actualizado = donanteService.actualizarPersonaHumana(5L, cambios);
        assertEquals("Roberto Carlos", actualizado.getNombre());
        assertEquals(41, actualizado.getEdad());
        assertEquals("PASAPORTE", actualizado.getTipoDocumento());
    }
}
