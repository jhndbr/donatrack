package com.donatrack.donaciones.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.enums.TipoNecesidad;
import com.donatrack.donaciones.domain.*;
import com.donatrack.donaciones.exception.DonacionYaAsignadaException;
import com.donatrack.donaciones.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DonacionServiceTest {

    @Mock
    private DonacionRepository donacionRepository;

    @Mock
    private PersonaDonanteRepository donanteRepository;

    @Mock
    private EntidadBeneficiariaRepository entidadRepository;

    @Mock
    private NecesidadMaterialRepository necesidadRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DonacionService donacionService;

    private PersonaHumana donante;
    private EntidadBeneficiaria entidad;

    @BeforeEach
    void setUp() {
        donante = new PersonaHumana("Juan", "Perez", 35, "30111222", "M", "Av. Siempreviva 123");
        donante.setId(1L);

        entidad = new EntidadBeneficiaria("Comedor Esperanza", "Av. Belgrano 500", "11-2222-3333");
        entidad.setId(10L);

        ReflectionTestUtils.setField(donacionService, "routingKeyDonacionAsignada", "donacion.asignada");
    }

    @Test
    void testSegmentacionAutomaticaPorSubcategoria() {
        when(donanteRepository.findById(1L)).thenReturn(Optional.of(donante));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BienDonado bien1 = new BienDonado("Fideos Tallarines", "Alimentos", "Pastas Secas", 10.0, "paquetes", false, null, null);
        BienDonado bien2 = new BienDonado("Fideos Tirabuzón", "Alimentos", "Pastas Secas", 15.0, "paquetes", false, null, null);
        BienDonado bien3 = new BienDonado("Camperas polar", "Indumentaria", "Abrigo", 5.0, "unidades", false, null, null);

        List<Donacion> resultado = donacionService.registrarCargaSegmentada(1L, List.of(bien1, bien2, bien3));

        // Debe haber agrupado los fideos en 1 donación de Pastas Secas y las camperas en otra donación de Abrigo
        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().anyMatch(d -> d.getSubcategoria().equals("Pastas Secas") && d.getCantidad() == 25.0));
        assertTrue(resultado.stream().anyMatch(d -> d.getSubcategoria().equals("Abrigo") && d.getCantidad() == 5.0));
        verify(donacionRepository, times(2)).save(any(Donacion.class));
    }

    @Test
    void testSegmentacionDonanteInexistenteLanzaExcepcion() {
        when(donanteRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                donacionService.registrarCargaSegmentada(999L, List.of())
        );
    }

    @Test
    void testAsignarDonacionExitosoConOutbox() throws Exception {
        Donacion d = new Donacion(donante, "Pastas Secas", 20.0, "kg", false, null);
        d.setId(100L);

        when(donacionRepository.findById(100L)).thenReturn(Optional.of(d));
        when(entidadRepository.findById(10L)).thenReturn(Optional.of(entidad));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"idDonacion\":100}");

        Donacion asignada = donacionService.asignarDonacion(100L, 10L);

        assertEquals(EstadoDonacion.ASIGNACION_REALIZADA, asignada.getEstado());
        assertEquals(entidad, asignada.getEntidadAsignada());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertEquals("Donacion", outbox.getAggregateType());
        assertEquals("100", outbox.getAggregateId());
        assertEquals("donacion.asignada", outbox.getRoutingKey());
    }

    @Test
    void testAsignarDonacionYaAsignadaLanzaDonacionYaAsignadaException() {
        Donacion d = new Donacion(donante, "Pastas Secas", 20.0, "kg", false, null);
        d.setId(100L);
        d.asignarA(entidad); // Ya está en ASIGNACION_REALIZADA

        when(donacionRepository.findById(100L)).thenReturn(Optional.of(d));

        assertThrows(DonacionYaAsignadaException.class, () -> donacionService.asignarDonacion(100L, 10L));
    }

    @Test
    void testAsignarDonacionInexistenteLanzaExcepcion() {
        when(donacionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> donacionService.asignarDonacion(999L, 10L));
    }

    @Test
    void testAsignarDonacionEntidadInexistenteLanzaExcepcion() {
        Donacion d = new Donacion(donante, "Pastas Secas", 20.0, "kg", false, null);
        when(donacionRepository.findById(100L)).thenReturn(Optional.of(d));
        when(entidadRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> donacionService.asignarDonacion(100L, 999L));
    }

    @Test
    void testDesasignarDonacionExitoso() {
        Donacion d = new Donacion(donante, "Pastas Secas", 20.0, "kg", false, null);
        d.setId(100L);
        NecesidadMaterial nec = new NecesidadMaterial("Pastas Secas", "Víveres", TipoNecesidad.EXTRAORDINARIA, 50.0, "kg", null);
        nec.setEntidadBeneficiaria(entidad);
        d.asignarA(entidad, nec);

        when(donacionRepository.findById(100L)).thenReturn(Optional.of(d));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Donacion desasignada = donacionService.desasignarDonacion(100L, "Reasignación", "ADMIN");

        assertEquals(EstadoDonacion.EN_DEPOSITO, desasignada.getEstado());
        assertNull(desasignada.getEntidadAsignada());
        assertNull(desasignada.getNecesidadAsignada());
        assertEquals(0.0, nec.getCantidadCubierta());
        verify(necesidadRepository, times(1)).save(nec);
    }

    @Test
    void testModificarDonacionSoloPermitidoEnDeposito() {
        Donacion d = new Donacion(donante, "Arroz", 10.0, "kg", false, null);
        d.setId(100L);

        when(donacionRepository.findById(100L)).thenReturn(Optional.of(d));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));

        // En depósito es editable
        Donacion mod = donacionService.modificarDonacion(100L, "Arroz Integral", 15.0, "kg", false);
        assertEquals("Arroz Integral", mod.getSubcategoria());
        assertEquals(15.0, mod.getCantidad());

        // Si cambia a ASIGNACION_REALIZADA, debe rechazar edición
        d.asignarA(entidad);
        assertThrows(IllegalStateException.class, () ->
                donacionService.modificarDonacion(100L, "Arroz Blanco", 20.0, "kg", false)
        );
    }

    @Test
    void testFiltrarDonacionesSpecification() {
        when(donacionRepository.findAll(any(Specification.class))).thenReturn(List.of(new Donacion()));

        List<Donacion> res = donacionService.filtrarDonaciones(
                List.of(EstadoDonacion.EN_DEPOSITO), "Arroz", 1L, null, null, null, false
        );

        assertNotNull(res);
        assertEquals(1, res.size());
        verify(donacionRepository, times(1)).findAll(any(Specification.class));
    }

    @Test
    void testAsignarDonacionFalloSerializacionJsonLanzaRuntime() throws Exception {
        Donacion d = new Donacion(donante, "Pastas Secas", 20.0, "kg", false, null);
        d.setId(100L);

        when(donacionRepository.findById(100L)).thenReturn(Optional.of(d));
        when(entidadRepository.findById(10L)).thenReturn(Optional.of(entidad));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error mock") {});

        assertThrows(RuntimeException.class, () -> donacionService.asignarDonacion(100L, 10L));
    }

    @Test
    void testActualizarEstadoTransicionesValidas() {
        Donacion d = new Donacion(donante, "Arroz", 10.0, "kg", false, null);
        d.setId(101L);
        d.asignarA(entidad);

        when(donacionRepository.findById(101L)).thenReturn(Optional.of(d));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. LISTA_PARA_ENTREGAR
        Donacion d1 = donacionService.actualizarEstado(101L, EstadoDonacion.LISTA_PARA_ENTREGAR, null);
        assertEquals(EstadoDonacion.LISTA_PARA_ENTREGAR, d1.getEstado());

        // 2. EN_TRASLADO
        Donacion d2 = donacionService.actualizarEstado(101L, EstadoDonacion.EN_TRASLADO, null);
        assertEquals(EstadoDonacion.EN_TRASLADO, d2.getEstado());

        // 3. ENTREGA_FALLIDA
        Donacion d3 = donacionService.actualizarEstado(101L, EstadoDonacion.ENTREGA_FALLIDA, "Dirección errónea");
        assertEquals(EstadoDonacion.ENTREGA_FALLIDA, d3.getEstado());

        // 4. EN_DEPOSITO (reingreso)
        Donacion d4 = donacionService.actualizarEstado(101L, EstadoDonacion.EN_DEPOSITO, null);
        assertEquals(EstadoDonacion.EN_DEPOSITO, d4.getEstado());

        // 5. VENCIDA
        Donacion d5 = donacionService.actualizarEstado(101L, EstadoDonacion.VENCIDA, null);
        assertEquals(EstadoDonacion.VENCIDA, d5.getEstado());
    }

    @Test
    void testActualizarEstadoConfirmarEntrega() {
        Donacion d = new Donacion(donante, "Arroz", 10.0, "kg", false, null);
        d.setId(101L);
        d.asignarA(entidad);
        d.marcarListaParaEntregar();
        d.iniciarTraslado();

        when(donacionRepository.findById(101L)).thenReturn(Optional.of(d));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));

        Donacion entregada = donacionService.actualizarEstado(101L, EstadoDonacion.ENTREGADA, null);
        assertEquals(EstadoDonacion.ENTREGADA, entregada.getEstado());
    }

    @Test
    void testActualizarEstadoInvalidoDirecto() {
        Donacion d = new Donacion(donante, "Arroz", 10.0, "kg", false, null);
        d.setId(101L);
        when(donacionRepository.findById(101L)).thenReturn(Optional.of(d));

        // ASIGNACION_REALIZADA no se permite directo por actualizarEstado sin entidad
        assertThrows(IllegalArgumentException.class, () ->
                donacionService.actualizarEstado(101L, EstadoDonacion.ASIGNACION_REALIZADA, null)
        );
    }

    @Test
    void testActualizarEstadoLote() {
        Donacion d1 = new Donacion(donante, "Arroz", 10.0, "kg", false, null);
        d1.asignarA(entidad);

        when(donacionRepository.findById(101L)).thenReturn(Optional.of(d1));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Donacion> actualizadas = donacionService.actualizarEstadoLote(
                List.of(101L),
                EstadoDonacion.LISTA_PARA_ENTREGAR,
                null
        );

        assertEquals(1, actualizadas.size());
        assertEquals(EstadoDonacion.LISTA_PARA_ENTREGAR, actualizadas.get(0).getEstado());
    }
}
