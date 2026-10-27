package com.donatrack.logistica.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.logistica.client.DonacionesServiceClient;
import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.dto.ComprobanteEntregaDto;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.ReplanificarRequestDto;
import com.donatrack.logistica.publisher.LogisticaEventPublisher;
import com.donatrack.logistica.repository.EntregaRepository;
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
public class EntregaServiceTest {

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private DonacionesServiceClient donacionesServiceClient;

    @Mock
    private LogisticaEventPublisher eventPublisher;

    @InjectMocks
    private EntregaServiceImpl entregaService;

    private Entrega entrega;

    @BeforeEach
    void setUp() {
        entrega = new Entrega("RUTA-01", 101L, 1L, "Comedor Esperanza", "entidad@mail.com", 10L, "donante@mail.com", "AF123CD");
        entrega.setId("ENT-101");
    }

    @Test
    void testConfirmarEntregaConFotosYReceptor() {
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));
        when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                List.of("https://s3.donatrack.org/foto1.png"),
                "Juan Pérez",
                "30123456",
                "https://s3.donatrack.org/firma.png"
        );

        Entrega confirmada = entregaService.confirmarEntrega("ENT-101", request);

        assertEquals(Entrega.EstadoEntrega.ENTREGADA, confirmada.getEstado());
        assertEquals("Juan Pérez", confirmada.getReceptorNombre());
        assertEquals(1, confirmada.getFotosConfirmacionUrls().size());
        verify(donacionesServiceClient, times(1)).actualizarEstadoDonacion(
                eq(101L), eq(EstadoDonacion.ENTREGADA), anyString(), anyString(), any()
        );
        verify(eventPublisher, times(1)).publicarEntregaConfirmada(any());
    }

    @Test
    void testReplanificarEntregaExitosoSiRegresoAlDeposito() {
        entrega.registrarNoRecibida("Cerrado", true);
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));
        when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> inv.getArgument(0));

        ReplanificarRequestDto request = new ReplanificarRequestDto(true, "Regreso en buen estado");
        Entrega replanificada = entregaService.replanificarEntrega("ENT-101", request);

        assertEquals(Entrega.EstadoEntrega.PENDIENTE, replanificada.getEstado());
        verify(donacionesServiceClient, times(1)).actualizarEstadoDonacion(
                eq(101L), eq(EstadoDonacion.EN_DEPOSITO), anyString(), anyString()
        );
    }

    @Test
    void testReplanificarEntregaFallaSiNoRegresoAlDeposito() {
        entrega.registrarNoRecibida("Perdida en tránsito", false);
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));

        ReplanificarRequestDto request = new ReplanificarRequestDto(false, "No se sabe el paradero");
        assertThrows(IllegalStateException.class, () -> entregaService.replanificarEntrega("ENT-101", request));
    }

    @Test
    void testObtenerComprobanteEntrega() {
        entrega.confirmarRecepcion(List.of("http://foto.png"), "Receptor", "123", "http://firma.png");
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));

        ComprobanteEntregaDto comprobante = entregaService.obtenerComprobante("ENT-101");

        assertNotNull(comprobante);
        assertEquals("ENT-101", comprobante.idEntrega());
        assertEquals("Receptor", comprobante.receptorNombre());
        assertEquals("ENTREGADA", comprobante.estado());
    }

    @Test
    void testConfirmarEntregaPorDonacionPasaDePendienteAEnTrasladoYConfirma() {
        assertEquals(Entrega.EstadoEntrega.PENDIENTE, entrega.getEstado());
        when(entregaRepository.findByIdDonacion(101L)).thenReturn(Optional.of(entrega));
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));
        when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> inv.getArgument(0));

        ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                List.of("https://s3.donatrack.org/foto1.png"),
                "María López",
                "28999888",
                null
        );

        Entrega confirmada = entregaService.confirmarEntregaPorDonacion(101L, request);
        assertEquals(Entrega.EstadoEntrega.ENTREGADA, confirmada.getEstado());
        assertEquals("RECEPCION_CONFIRMADA", confirmada.getEtapaOperativa());
        assertTrue(confirmada.isConfirmadoPorChofer());
        assertTrue(confirmada.isConfirmadoPorEntidad());
        assertEquals("María López", confirmada.getReceptorNombre());
    }

    @Test
    void testRegistrarArriboSedePorDonacionActualizaEtapaOperativa() {
        when(entregaRepository.findByIdDonacion(101L)).thenReturn(Optional.of(entrega));
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));
        when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> inv.getArgument(0));

        Entrega enPuerta = entregaService.registrarArriboSedePorDonacion(101L, "Camión en puerta de Comedor Esperanza");
        assertEquals(Entrega.EstadoEntrega.EN_TRASLADO, enPuerta.getEstado());
        assertEquals("EN_PUERTA_SEDE", enPuerta.getEtapaOperativa());
        assertTrue(enPuerta.isConfirmadoPorChofer());
        assertNotNull(enPuerta.getFechaArriboSede());
    }

    @Test
    void testConfirmarEntregaEntidadNoAutorizadaLanzaConflictException() {
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));
        // entrega belongs to idEntidadBeneficiaria = 1L. Request is from entity 99L.
        ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                List.of("https://s3.donatrack.org/foto1.png"),
                "Receptor Ilegítimo",
                "12345678",
                null,
                99L
        );

        assertThrows(com.donatrack.logistica.exception.ConflictException.class, () ->
                entregaService.confirmarEntrega("ENT-101", request)
        );
    }

    @Test
    void testConfirmarEntregaIdempotenteNoDuplicaEventos() {
        entrega.confirmarRecepcion(List.of("http://foto.png"), "Laura", "30111222", null);
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));

        ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                List.of("https://s3.donatrack.org/foto1.png"),
                "Laura",
                "30111222",
                null,
                1L
        );

        Entrega yaEntregada = entregaService.confirmarEntrega("ENT-101", request);
        assertEquals(Entrega.EstadoEntrega.ENTREGADA, yaEntregada.getEstado());
        verify(eventPublisher, never()).publicarEntregaConfirmada(any());
    }

    @Test
    void testAgregarEvidenciasExitoso() {
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));
        when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> inv.getArgument(0));

        Entrega conEvidencia = entregaService.agregarEvidencias("ENT-101", List.of("http://remito1.jpg", "http://remito2.jpg"));
        assertEquals(2, conEvidencia.getFotosConfirmacionUrls().size());
        assertTrue(conEvidencia.getFotosConfirmacionUrls().contains("http://remito1.jpg"));
    }

    @Test
    void testAgregarEvidenciasVacioLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
                entregaService.agregarEvidencias("ENT-101", List.of())
        );
    }

    @Test
    void testReplanificarEntregaYaEntregadaLanzaExcepcion() {
        entrega.confirmarRecepcion(List.of("http://foto.png"), "Laura", "30111222", null);
        when(entregaRepository.findById("ENT-101")).thenReturn(Optional.of(entrega));

        ReplanificarRequestDto request = new ReplanificarRequestDto(true, "Intento de reversión inválido");
        assertThrows(IllegalStateException.class, () ->
                entregaService.replanificarEntrega("ENT-101", request)
        );
    }
}
