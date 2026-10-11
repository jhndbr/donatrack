package com.donatrack.logistica.service;

import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.logistica.domain.*;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LogisticaBatchRutaTest {

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private CamionRepository camionRepository;

    @Mock
    private UbicacionGPSRepository gpsRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private LogisticaService logisticaService;

    private Camion camion;

    @BeforeEach
    void setUp() {
        camion = new Camion("AE789XY", 20.0, 3.0, 3500.0);
        camion.setId("CAMION-01");
        org.springframework.test.util.ReflectionTestUtils.setField(logisticaService, "exchange", "donatrack.events.exchange");
        org.springframework.test.util.ReflectionTestUtils.setField(logisticaService, "routingKeyRutaIniciada", "ruta.iniciada");
        org.springframework.test.util.ReflectionTestUtils.setField(logisticaService, "routingKeyEntregaConfirmada", "entrega.confirmada");
        org.springframework.test.util.ReflectionTestUtils.setField(logisticaService, "routingKeyEntregaFallida", "entrega.fallida");
    }

    @Test
    void testParticionamientoEstrictoLotesMaximo100() {
        when(camionRepository.findById("CAMION-01")).thenReturn(Optional.of(camion));

        // Crear lista de 250 donaciones
        List<Long> donaciones250 = new ArrayList<>();
        for (long i = 1; i <= 250; i++) {
            donaciones250.add(i);
        }

        List<PlanificadorCallbackDto> lotes = logisticaService.planificarLotes(donaciones250, "CAMION-01");

        // Debe haber generado 3 lotes: 100, 100 y 50
        assertEquals(3, lotes.size());
        assertEquals(100, lotes.get(0).getRutasGeneradas().get(0).getParadas().size());
        assertEquals(100, lotes.get(1).getRutasGeneradas().get(0).getParadas().size());
        assertEquals(50, lotes.get(2).getRutasGeneradas().get(0).getParadas().size());
    }

    @Test
    void testPlanificarLotesVacioLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
                logisticaService.planificarLotes(Collections.emptyList(), "CAMION-01")
        );
        assertThrows(IllegalArgumentException.class, () ->
                logisticaService.planificarLotes(null, "CAMION-01")
        );
    }

    @Test
    void testIniciarRutaActualizaEntregasYEmiteEvento() {
        Ruta ruta = new Ruta("CAMION-01", "AE789XY", java.time.LocalDate.now());
        ruta.setId("RUTA-01");

        when(rutaRepository.findById("RUTA-01")).thenReturn(Optional.of(ruta));
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(inv -> inv.getArgument(0));

        Entrega entrega = new Entrega("RUTA-01", 101L, 1L, "Comedor", "entidad@mail.com", 50L, "donante@mail.com", "AE789XY");
        when(entregaRepository.findByIdRuta("RUTA-01")).thenReturn(List.of(entrega));

        Ruta iniciada = logisticaService.iniciarRuta("RUTA-01");

        assertEquals(Ruta.EstadoRuta.EN_CURSO, iniciada.getEstado());
        assertEquals(Entrega.EstadoEntrega.EN_TRASLADO, entrega.getEstado());
        verify(rabbitTemplate, times(1)).convertAndSend(eq("donatrack.events.exchange"), eq("ruta.iniciada"), any(Object.class));
    }

    @Test
    void testIniciarRutaInexistenteLanzaExcepcion() {
        when(rutaRepository.findById("RUTA-999")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> logisticaService.iniciarRuta("RUTA-999"));
    }

    @Test
    void testConfirmarEntregaExitosoConFotos() {
        Entrega entrega = new Entrega("RUTA-01", 101L, 1L, "Comedor", "entidad@mail.com", 50L, "donante@mail.com", "AE789XY");
        entrega.setId("ENTREGA-01");

        when(entregaRepository.findById("ENTREGA-01")).thenReturn(Optional.of(entrega));
        when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> fotos = List.of("https://s3.aws.com/donatrack/foto1.jpg", "https://s3.aws.com/donatrack/foto2.jpg");
        Entrega confirmada = logisticaService.confirmarEntrega("ENTREGA-01", fotos);

        assertEquals(Entrega.EstadoEntrega.ENTREGADA, confirmada.getEstado());
        assertEquals(2, confirmada.getFotosConfirmacionUrls().size());
        assertNotNull(confirmada.getFechaActualizacion());

        ArgumentCaptor<EntregaConfirmadaEvent> captor = ArgumentCaptor.forClass(EntregaConfirmadaEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq("donatrack.events.exchange"), eq("entrega.confirmada"), captor.capture());
        EntregaConfirmadaEvent evento = captor.getValue();
        assertEquals(101L, evento.getIdDonacion());
        assertEquals("donante@mail.com", evento.getEmailDonante());
    }

    @Test
    void testConfirmarEntregaInexistenteLanzaExcepcion() {
        when(entregaRepository.findById("ENTREGA-999")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                logisticaService.confirmarEntrega("ENTREGA-999", List.of())
        );
    }

    @Test
    void testRegistrarEntregaFallida() {
        Entrega entrega = new Entrega("RUTA-01", 101L, 1L, "Comedor", "entidad@mail.com", 50L, "donante@mail.com", "AE789XY");
        entrega.setId("ENTREGA-01");

        when(entregaRepository.findById("ENTREGA-01")).thenReturn(Optional.of(entrega));
        when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> inv.getArgument(0));

        Entrega fallida = logisticaService.registrarEntregaFallida("ENTREGA-01", "Comedor cerrado por reformas", true);

        assertEquals(Entrega.EstadoEntrega.NO_RECIBIDA, fallida.getEstado());
        assertEquals("Comedor cerrado por reformas", fallida.getMotivoFalla());

        ArgumentCaptor<EntregaFallidaEvent> captor = ArgumentCaptor.forClass(EntregaFallidaEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq("donatrack.events.exchange"), eq("entrega.fallida"), captor.capture());
        EntregaFallidaEvent evento = captor.getValue();
        assertEquals("Comedor cerrado por reformas", evento.getMotivoFalla());
        assertTrue(evento.isReplanificable());
    }

    @Test
    void testRegistrarTelemetriaGPS() {
        UbicacionGPS ubicacion = new UbicacionGPS("CAMION-01", "AE789XY", -34.6037, -58.3816, 45.0);
        when(gpsRepository.save(any(UbicacionGPS.class))).thenReturn(ubicacion);

        UbicacionGPS resultado = logisticaService.registrarTelemetriaGPS("CAMION-01", "AE789XY", -34.6037, -58.3816, 45.0);

        assertNotNull(resultado);
        assertEquals("AE789XY", resultado.getPatente());
        assertEquals(-34.6037, resultado.getLatitud());
        assertEquals(-58.3816, resultado.getLongitud());
        assertEquals(45.0, resultado.getVelocidadKmH());
        verify(gpsRepository, times(1)).save(any(UbicacionGPS.class));
    }
}
