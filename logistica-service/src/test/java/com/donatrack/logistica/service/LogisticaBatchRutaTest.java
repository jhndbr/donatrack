package com.donatrack.logistica.service;

import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.common.events.RutaIniciadaEvent;
import com.donatrack.logistica.client.DonacionesServiceClient;
import com.donatrack.logistica.client.MockVrpOptimizerClient;
import com.donatrack.logistica.client.RutaOptimizerClient;
import com.donatrack.logistica.domain.*;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.EntregaFallidaRequestDto;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.dto.TelemetriaRequestDto;
import com.donatrack.logistica.exception.ResourceNotFoundException;
import com.donatrack.logistica.publisher.LogisticaEventPublisher;
import com.donatrack.logistica.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private DonacionPendienteLoteRepository pendienteLoteRepository;

    @Mock
    private DonacionesServiceClient donacionesServiceClient;

    @Mock
    private LogisticaEventPublisher eventPublisher;

    private FlotaService flotaService;
    private PlanificacionRutasService planificacionService;
    private RecorridoService recorridoService;
    private EntregaService entregaService;
    private TelemetriaService telemetriaService;
    private LogisticaService logisticaService;

    private Camion camion;

    @BeforeEach
    void setUp() {
        flotaService = new FlotaServiceImpl(camionRepository, rutaRepository);
        RutaOptimizerClient optimizerClient = new MockVrpOptimizerClient();
        planificacionService = new PlanificacionRutasServiceImpl(
                flotaService, optimizerClient, donacionesServiceClient,
                rutaRepository, entregaRepository, pendienteLoteRepository
        );
        recorridoService = new RecorridoServiceImpl(
                rutaRepository, entregaRepository, donacionesServiceClient, eventPublisher
        );
        entregaService = new EntregaServiceImpl(
                entregaRepository, donacionesServiceClient, eventPublisher
        );
        telemetriaService = new TelemetriaServiceImpl(
                camionRepository, gpsRepository, rutaRepository
        );
        logisticaService = new LogisticaService(
                planificacionService, recorridoService, entregaService, telemetriaService
        );

        camion = new Camion("AE789XY", 20.0, 3.0, 3500.0);
        camion.setId("CAMION-01");
        lenient().when(rutaRepository.save(any(Ruta.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(rutaRepository.findByLoteId(anyString())).thenReturn(Collections.emptyList());
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

        // Debe haber generado 3 lotes: 100, 100 y 50 (restricción estricta <= 100)
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
        verify(eventPublisher, times(1)).publicarRutaIniciada(any(RutaIniciadaEvent.class));
    }

    @Test
    void testIniciarRutaInexistenteLanzaExcepcion() {
        when(rutaRepository.findById("RUTA-999")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> logisticaService.iniciarRuta("RUTA-999"));
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
        verify(eventPublisher, times(1)).publicarEntregaConfirmada(captor.capture());
        EntregaConfirmadaEvent evento = captor.getValue();
        assertEquals(101L, evento.getIdDonacion());
        assertEquals("donante@mail.com", evento.getEmailDonante());
    }

    @Test
    void testConfirmarEntregaSinFotosLanzaExcepcion() {
        Entrega entrega = new Entrega("RUTA-01", 101L, 1L, "Comedor", "entidad@mail.com", 50L, "donante@mail.com", "AE789XY");
        entrega.setId("ENTREGA-01");
        when(entregaRepository.findById("ENTREGA-01")).thenReturn(Optional.of(entrega));

        assertThrows(IllegalArgumentException.class, () ->
                logisticaService.confirmarEntrega("ENTREGA-01", List.of())
        );
    }

    @Test
    void testConfirmarEntregaInexistenteLanzaExcepcion() {
        when(entregaRepository.findById("ENTREGA-999")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                logisticaService.confirmarEntrega("ENTREGA-999", List.of("http://foto.jpg"))
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
        verify(eventPublisher, times(1)).publicarEntregaFallida(captor.capture());
        EntregaFallidaEvent evento = captor.getValue();
        assertEquals("Comedor cerrado por reformas", evento.getMotivoFalla());
        assertTrue(evento.isReplanificable());
    }

    @Test
    void testRegistrarEntregaFallidaSinMotivoLanzaExcepcion() {
        Entrega entrega = new Entrega("RUTA-01", 101L, 1L, "Comedor", "entidad@mail.com", 50L, "donante@mail.com", "AE789XY");
        entrega.setId("ENTREGA-01");
        when(entregaRepository.findById("ENTREGA-01")).thenReturn(Optional.of(entrega));

        assertThrows(IllegalArgumentException.class, () ->
                logisticaService.registrarEntregaFallida("ENTREGA-01", "   ", false)
        );
    }

    @Test
    void testRegistrarTelemetriaGPS() {
        UbicacionGPS ubicacion = new UbicacionGPS("CAMION-01", "AE789XY", -34.6037, -58.3816, 45.0);
        when(camionRepository.findById("CAMION-01")).thenReturn(Optional.of(camion));
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
