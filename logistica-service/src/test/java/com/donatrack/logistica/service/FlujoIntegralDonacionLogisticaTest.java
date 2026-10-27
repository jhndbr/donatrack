package com.donatrack.logistica.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.common.events.RutaIniciadaEvent;
import com.donatrack.logistica.client.DonacionesServiceClient;
import com.donatrack.logistica.client.MockVrpOptimizerClient;
import com.donatrack.logistica.domain.*;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.EntregaFallidaRequestDto;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.dto.ReplanificarRequestDto;
import com.donatrack.logistica.exception.ConflictException;
import com.donatrack.logistica.publisher.LogisticaEventPublisher;
import com.donatrack.logistica.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Suite integral de pruebas que valida de punta a punta el ciclo completo de donaciones:
 * Desde la planificación de la ruta, la recepción del chofer en depósito,
 * el traslado y arribo a puerta, la entrega y recepción de la entidad benéfica con fotografías,
 * la emisión de eventos de notificaciones, y todos los flujos de fallo e invariantes operativas.
 */
@ExtendWith(MockitoExtension.class)
public class FlujoIntegralDonacionLogisticaTest {

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

    private FlotaServiceImpl flotaService;
    private PlanificacionRutasServiceImpl planificacionService;
    private RecorridoServiceImpl recorridoService;
    private EntregaServiceImpl entregaService;
    private TelemetriaServiceImpl telemetriaService;

    private Camion camion;
    private Map<String, Ruta> rutaDatabase;
    private Map<String, Entrega> entregaDatabase;

    @BeforeEach
    void setUp() {
        rutaDatabase = new HashMap<>();
        entregaDatabase = new HashMap<>();

        camion = new Camion("AE789XY", "Mercedes Benz Atego", 25.0, 3.2, 4500.0);
        camion.setId("CAM-01");

        flotaService = new FlotaServiceImpl(camionRepository, rutaRepository);
        planificacionService = new PlanificacionRutasServiceImpl(
                flotaService,
                new MockVrpOptimizerClient(),
                donacionesServiceClient,
                rutaRepository,
                entregaRepository,
                pendienteLoteRepository
        );
        recorridoService = new RecorridoServiceImpl(
                rutaRepository,
                entregaRepository,
                donacionesServiceClient,
                eventPublisher
        );
        entregaService = new EntregaServiceImpl(
                entregaRepository,
                donacionesServiceClient,
                eventPublisher
        );
        telemetriaService = new TelemetriaServiceImpl(
                camionRepository,
                gpsRepository,
                rutaRepository
        );

        // Inyectar dependencias opcionales en entregaService
        ReflectionTestUtils.setField(entregaService, "rutaRepository", rutaRepository);
        ReflectionTestUtils.setField(entregaService, "telemetriaService", telemetriaService);

        // Simular persistencia en memoria para repositorios
        lenient().when(rutaRepository.save(any(Ruta.class))).thenAnswer(inv -> {
            Ruta r = inv.getArgument(0);
            if (r.getId() == null) r.setId("RUTA-" + UUID.randomUUID());
            rutaDatabase.put(r.getId(), r);
            return r;
        });
        lenient().when(rutaRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.ofNullable(rutaDatabase.get(id));
        });
        lenient().when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> {
            Entrega e = inv.getArgument(0);
            if (e.getId() == null) e.setId("ENT-" + UUID.randomUUID());
            entregaDatabase.put(e.getId(), e);
            return e;
        });
        lenient().when(entregaRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return Optional.ofNullable(entregaDatabase.get(id));
        });
        lenient().when(entregaRepository.findByIdDonacion(anyLong())).thenAnswer(inv -> {
            Long idDon = inv.getArgument(0);
            return entregaDatabase.values().stream()
                    .filter(e -> idDon.equals(e.getIdDonacion()))
                    .findFirst();
        });
        lenient().when(entregaRepository.findByIdRuta(anyString())).thenAnswer(inv -> {
            String idRuta = inv.getArgument(0);
            return entregaDatabase.values().stream()
                    .filter(e -> idRuta.equals(e.getIdRuta()))
                    .toList();
        });
    }

    @Nested
    @DisplayName("Flujo Feliz: Donación -> Planificación -> Chofer -> Entrega -> Recepción Entidad")
    class FlujoExitoso {

        @Test
        @DisplayName("Debe ejecutar el ciclo de vida completo con todas sus transiciones y eventos")
        void testCicloCompletoDonacionHastaRecepcionExitosa() {
            // GIVEN: Donación en ASIGNACION_REALIZADA asignada a Comedor Infantil (ID 10)
            Long idDonacion = 501L;
            Long idEntidad = 10L;
            Long idDonante = 88L;
            String emailDonante = "donante.solidario@mail.com";
            String emailEntidad = "comedor.infantil@mail.com";

            when(camionRepository.findById("CAM-01")).thenReturn(Optional.of(camion));
            when(donacionesServiceClient.obtenerDetalleDonacion(idDonacion)).thenReturn(Map.of(
                    "id", idDonacion,
                    "subcategoria", "Leche Larga Vida",
                    "cantidad", 50.0,
                    "unidadMedida", "litros",
                    "donante", Map.of(
                            "id", idDonante,
                            "emailPrincipal", emailDonante
                    ),
                    "entidadAsignada", Map.of(
                            "id", idEntidad,
                            "razonSocial", "Comedor Infantil Los Bajitos",
                            "emailPrincipal", emailEntidad,
                            "direccion", "Av. San Martín 1500, Quilmes",
                            "latitud", -34.7200,
                            "longitud", -58.2600
                    )
            ));

            // PASO 1: Planificación de ruta por el servicio logístico
            List<PlanificadorCallbackDto> lotes = planificacionService.planificarLotes(List.of(idDonacion), "CAM-01");
            assertNotNull(lotes);
            assertFalse(lotes.isEmpty());

            // Verificar que se haya sincronizado a LISTA_PARA_ENTREGAR en donaciones-service
            verify(donacionesServiceClient, times(1)).actualizarEstadoLote(
                    eq(List.of(idDonacion)),
                    eq(EstadoDonacion.LISTA_PARA_ENTREGAR),
                    contains("Asignadas formalmente en lote")
            );

            // Obtener la ruta y la entrega creadas
            assertEquals(1, rutaDatabase.size());
            Ruta rutaCreada = rutaDatabase.values().iterator().next();
            assertEquals(Ruta.EstadoRuta.PLANIFICADA, rutaCreada.getEstado());
            assertEquals("AE789XY", rutaCreada.getPatenteCamion());
            assertEquals(1, rutaCreada.getParadas().size());

            assertEquals(1, entregaDatabase.size());
            Entrega entregaCreada = entregaDatabase.values().iterator().next();
            assertEquals(Entrega.EstadoEntrega.PENDIENTE, entregaCreada.getEstado());
            assertEquals("PENDIENTE_RETIRO_DEPOSITO", entregaCreada.getEtapaOperativa());
            assertEquals(idDonacion, entregaCreada.getIdDonacion());
            assertEquals(idEntidad, entregaCreada.getIdEntidadBeneficiaria());

            // PASO 2: Recepción del chofer en depósito e inicio de ruta
            Ruta rutaIniciada = recorridoService.iniciarRuta(rutaCreada.getId());
            assertEquals(Ruta.EstadoRuta.EN_CURSO, rutaIniciada.getEstado());
            assertNotNull(rutaIniciada.getFechaInicio());

            // Las entregas y paradas deben haber pasado a EN_TRASLADO
            Entrega entregaEnTraslado = entregaDatabase.get(entregaCreada.getId());
            assertEquals(Entrega.EstadoEntrega.EN_TRASLADO, entregaEnTraslado.getEstado());
            assertEquals("CARGADA_EN_CAMION", entregaEnTraslado.getEtapaOperativa());
            assertNotNull(entregaEnTraslado.getFechaRetiroDeposito());

            // Sincronización en donaciones-service a EN_TRASLADO
            verify(donacionesServiceClient, times(1)).actualizarEstadoLote(
                    eq(List.of(idDonacion)),
                    eq(EstadoDonacion.EN_TRASLADO),
                    contains("Ruta iniciada por chofer en camión AE789XY")
            );

            // Emisión de evento RutaIniciadaEvent con mapa en tiempo real
            ArgumentCaptor<RutaIniciadaEvent> rutaEventCaptor = ArgumentCaptor.forClass(RutaIniciadaEvent.class);
            verify(eventPublisher, times(1)).publicarRutaIniciada(rutaEventCaptor.capture());
            RutaIniciadaEvent rutaIniciadaEvent = rutaEventCaptor.getValue();
            assertEquals("AE789XY", rutaIniciadaEvent.getPatenteCamion());
            assertTrue(rutaIniciadaEvent.getEmailsNotificar().contains(emailDonante));
            assertTrue(rutaIniciadaEvent.getEmailsNotificar().contains(emailEntidad));
            assertNotNull(rutaIniciadaEvent.getLinkSeguimiento());

            // PASO 3: Llegada del chofer en puerta de la entidad benéfica
            Entrega entregaEnPuerta = entregaService.registrarArriboSedePorDonacion(idDonacion, "Camión en rampa de descarga");
            assertEquals(Entrega.EstadoEntrega.EN_TRASLADO, entregaEnPuerta.getEstado());
            assertEquals("EN_PUERTA_SEDE", entregaEnPuerta.getEtapaOperativa());
            assertTrue(entregaEnPuerta.isConfirmadoPorChofer());
            assertNotNull(entregaEnPuerta.getFechaArriboSede());
            assertEquals("EN_PUERTA_SEDE", rutaCreada.getParadas().get(0).getEstadoParada());

            // PASO 4: Recepción y confirmación formal por la entidad benéfica con fotografías
            ConfirmarEntregaRequestDto confirmacionDto = new ConfirmarEntregaRequestDto(
                    List.of("https://s3.donatrack.org/evidencias/leche-recibida-01.jpg", "https://s3.donatrack.org/evidencias/remito-firmado.jpg"),
                    "Marta Gómez (Directora Comedor)",
                    "27444555",
                    "https://s3.donatrack.org/firmas/marta-gomez.png",
                    idEntidad
            );

            Entrega entregaConfirmada = entregaService.confirmarEntregaPorDonacion(idDonacion, confirmacionDto);
            assertEquals(Entrega.EstadoEntrega.ENTREGADA, entregaConfirmada.getEstado());
            assertEquals("RECEPCION_CONFIRMADA", entregaConfirmada.getEtapaOperativa());
            assertTrue(entregaConfirmada.isConfirmadoPorEntidad());
            assertEquals("Marta Gómez (Directora Comedor)", entregaConfirmada.getReceptorNombre());
            assertEquals(2, entregaConfirmada.getFotosConfirmacionUrls().size());
            assertTrue(rutaCreada.getParadas().get(0).isCompletada());
            assertEquals("ENTREGADA", rutaCreada.getParadas().get(0).getEstadoParada());

            // Sincronización en donaciones-service a ENTREGADA con foto
            verify(donacionesServiceClient, times(1)).actualizarEstadoDonacion(
                    eq(idDonacion),
                    eq(EstadoDonacion.ENTREGADA),
                    contains("Recepción confirmada con fotografías en sede por Marta Gómez"),
                    eq("LOGISTICA_SERVICE"),
                    eq("https://s3.donatrack.org/evidencias/leche-recibida-01.jpg")
            );

            // Emisión de evento EntregaConfirmadaEvent para notificaciones e incentivos
            ArgumentCaptor<EntregaConfirmadaEvent> entregaConfirmadaCaptor = ArgumentCaptor.forClass(EntregaConfirmadaEvent.class);
            verify(eventPublisher, times(1)).publicarEntregaConfirmada(entregaConfirmadaCaptor.capture());
            EntregaConfirmadaEvent eventoConfirmado = entregaConfirmadaCaptor.getValue();
            assertEquals(idDonacion, eventoConfirmado.getIdDonacion());
            assertEquals(idDonante, eventoConfirmado.getIdDonante());
            assertEquals(idEntidad, eventoConfirmado.getIdEntidadBeneficiaria());
            assertEquals("AE789XY", eventoConfirmado.getPatenteCamion());
            assertEquals(emailDonante, eventoConfirmado.getEmailDonante());
            assertEquals(emailEntidad, eventoConfirmado.getEmailEntidad());

            // PASO 5: Carga de evidencias fotográficas adicionales posteriores
            Entrega conEvidenciaAdicional = entregaService.agregarEvidenciasPorDonacion(
                    idDonacion,
                    List.of("https://s3.donatrack.org/evidencias/acta-conformidad.pdf")
            );
            assertEquals(3, conEvidenciaAdicional.getFotosConfirmacionUrls().size());

            // PASO 6: Finalización de la hoja de ruta por el chofer
            Ruta rutaFinalizada = recorridoService.finalizarRuta(rutaCreada.getId());
            assertEquals(Ruta.EstadoRuta.FINALIZADA, rutaFinalizada.getEstado());
            assertNotNull(rutaFinalizada.getFechaFin());
        }
    }

    @Nested
    @DisplayName("Flujos de Fallo, Rechazo de Entrega y Contingencias (CU-44, CU-45)")
    class FlujosDeFallo {

        private String rutaId;
        private Long idDonacion;
        private Long idEntidad;
        private Long idDonante;

        @BeforeEach
        void prepararEntregaEnTraslado() {
            idDonacion = 701L;
            idEntidad = 25L;
            idDonante = 99L;

            Ruta ruta = new Ruta("CAM-01", "AE789XY", LocalDate.now());
            ruta.setId("RUTA-FALLO-01");
            Parada parada = new Parada(1, idEntidad, "Hogar Sol Naciente", "Calle 123", -34.6, -58.4, idDonacion);
            ruta.agregarParada(parada);
            ruta.iniciarRuta();
            rutaDatabase.put(ruta.getId(), ruta);
            this.rutaId = ruta.getId();

            Entrega entrega = new Entrega(
                    ruta.getId(),
                    idDonacion,
                    idEntidad,
                    "Hogar Sol Naciente",
                    "hogar@mail.com",
                    idDonante,
                    "donante701@mail.com",
                    "AE789XY"
            );
            entrega.setId("ENT-FALLO-701");
            entrega.marcarEnTraslado();
            entregaDatabase.put(entrega.getId(), entrega);
        }

        @Test
        @DisplayName("CU-44: Debe registrar entrega no recibida/fallida con motivo justificado y emitir evento")
        void testRegistrarEntregaFallidaExitoso() {
            EntregaFallidaRequestDto request = new EntregaFallidaRequestDto(
                    "Sede cerrada sin personal presente tras 30 minutos de espera reglamentaria",
                    true
            );

            Entrega fallida = entregaService.registrarEntregaFallidaPorDonacion(idDonacion, request);

            assertEquals(Entrega.EstadoEntrega.NO_RECIBIDA, fallida.getEstado());
            assertEquals("INCIDENCIA_REGISTRADA", fallida.getEtapaOperativa());
            assertEquals("Sede cerrada sin personal presente tras 30 minutos de espera reglamentaria", fallida.getMotivoFalla());
            assertTrue(fallida.isReplanificable());

            // Parada sincronizada a FALLIDA
            Ruta ruta = rutaDatabase.get(rutaId);
            assertEquals("FALLIDA", ruta.getParadas().get(0).getEstadoParada());
            assertEquals("Sede cerrada sin personal presente tras 30 minutos de espera reglamentaria", ruta.getParadas().get(0).getMotivoFalla());

            // Donación sincronizada a ENTREGA_FALLIDA en donaciones-service
            verify(donacionesServiceClient, times(1)).actualizarEstadoDonacion(
                    eq(idDonacion),
                    eq(EstadoDonacion.ENTREGA_FALLIDA),
                    contains("Incidencia logística reportada: Sede cerrada"),
                    eq("CHOFER")
            );

            // Publicación de evento EntregaFallidaEvent
            ArgumentCaptor<EntregaFallidaEvent> captor = ArgumentCaptor.forClass(EntregaFallidaEvent.class);
            verify(eventPublisher, times(1)).publicarEntregaFallida(captor.capture());
            EntregaFallidaEvent evento = captor.getValue();
            assertEquals(idDonacion, evento.getIdDonacion());
            assertEquals("Sede cerrada sin personal presente tras 30 minutos de espera reglamentaria", evento.getMotivoFalla());
            assertTrue(evento.isReplanificable());
        }

        @Test
        @DisplayName("CU-44 Fallo: Motivo de entrega fallida obligatorio y no puede ser blanco o nulo")
        void testRegistrarEntregaFallidaSinMotivoLanzaIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () ->
                    entregaService.registrarEntregaFallidaPorDonacion(idDonacion, new EntregaFallidaRequestDto(null, true))
            );
            assertThrows(IllegalArgumentException.class, () ->
                    entregaService.registrarEntregaFallidaPorDonacion(idDonacion, new EntregaFallidaRequestDto("   ", true))
            );
        }

        @Test
        @DisplayName("CU-45 Fallo: Replanificar sin retorno físico a depósito debe ser rechazado")
        void testReplanificarSinRegresoADepositoLanzaIllegalStateException() {
            // Se registra la incidencia
            entregaService.registrarEntregaFallidaPorDonacion(
                    idDonacion,
                    new EntregaFallidaRequestDto("Mercadería dañada en trayecto", false)
            );

            // Intento de replanificar cuando regresoAlDeposito == false
            ReplanificarRequestDto requestInvalido = new ReplanificarRequestDto(false, "No regresó al depósito");
            assertThrows(IllegalStateException.class, () ->
                    entregaService.replanificarEntregaPorDonacion(idDonacion, requestInvalido)
            );
        }

        @Test
        @DisplayName("CU-45 Éxito: Replanificación tras retorno físico a depósito restablece a PENDIENTE")
        void testReplanificarConRegresoFisicoADepositoExitoso() {
            // 1. Incidencia
            entregaService.registrarEntregaFallidaPorDonacion(
                    idDonacion,
                    new EntregaFallidaRequestDto("Sin espacio en sede receptora", true)
            );

            // 2. Administrador constata que el camión volvió al depósito con los bultos en buen estado
            ReplanificarRequestDto requestValido = new ReplanificarRequestDto(true, "Bienes descargados en depósito central en perfecto estado");
            Entrega replanificada = entregaService.replanificarEntregaPorDonacion(idDonacion, requestValido);

            assertEquals(Entrega.EstadoEntrega.PENDIENTE, replanificada.getEstado());
            assertEquals("PENDIENTE_RETIRO_DEPOSITO", replanificada.getEtapaOperativa());
            assertFalse(replanificada.isConfirmadoPorChofer());
            assertFalse(replanificada.isConfirmadoPorEntidad());

            // Parada restablecida a PENDIENTE
            Ruta ruta = rutaDatabase.get(rutaId);
            assertEquals("PENDIENTE", ruta.getParadas().get(0).getEstadoParada());
            assertNull(ruta.getParadas().get(0).getMotivoFalla());

            // Sincronización en donaciones-service a EN_DEPOSITO
            verify(donacionesServiceClient, times(1)).actualizarEstadoDonacion(
                    eq(idDonacion),
                    eq(EstadoDonacion.EN_DEPOSITO),
                    contains("Reingreso físico al depósito tras replanificación administrativa"),
                    eq("ADMIN")
            );
        }

        @Test
        @DisplayName("Seguridad: Entidad ajena no puede confirmar entrega asignada a otra entidad")
        void testConfirmarEntregaEntidadNoAutorizadaLanzaConflictException() {
            ConfirmarEntregaRequestDto requestAjeno = new ConfirmarEntregaRequestDto(
                    List.of("https://foto.png"),
                    "Intruso",
                    "11223344",
                    null,
                    999L // ID de entidad diferente al asignado (25L)
            );

            assertThrows(ConflictException.class, () ->
                    entregaService.confirmarEntregaPorDonacion(idDonacion, requestAjeno)
            );
        }

        @Test
        @DisplayName("Regla de Negocio: La entidad no puede confirmar si el chofer no marcó arribo en puerta")
        void testConfirmarSinArriboEnPuertaLanzaConflictException() {
            ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                    List.of("http://foto.jpg"),
                    "Receptor",
                    "11223344",
                    null,
                    idEntidad
            );

            ConflictException ex = assertThrows(ConflictException.class, () ->
                    entregaService.confirmarEntregaPorDonacion(idDonacion, request)
            );
            assertTrue(ex.getMessage().contains("aún no ha sido notificada en puerta por el chofer"));
        }

        @Test
        @DisplayName("Invariante: No se puede confirmar una entrega sin adjuntar fotografías")
        void testConfirmarSinFotografiasLanzaIllegalArgumentException() {
            entregaService.registrarArriboSedePorDonacion(idDonacion, "En puerta de sede");
            ConfirmarEntregaRequestDto sinFotos = new ConfirmarEntregaRequestDto(
                    List.of(),
                    "Receptor",
                    "11223344",
                    null,
                    idEntidad
            );

            assertThrows(IllegalArgumentException.class, () ->
                    entregaService.confirmarEntregaPorDonacion(idDonacion, sinFotos)
            );
        }

        @Test
        @DisplayName("Invariante: No se puede replanificar una donación que ya fue ENTREGADA")
        void testReplanificarEntregaYaEntregadaLanzaIllegalStateException() {
            entregaService.registrarArriboSedePorDonacion(idDonacion, "En puerta de sede");
            // Confirmar entrega
            entregaService.confirmarEntregaPorDonacion(
                    idDonacion,
                    new ConfirmarEntregaRequestDto(List.of("http://foto.jpg"), "Receptor", "123", null, idEntidad)
            );

            // Intentar replanificar una entrega ya completada
            assertThrows(IllegalStateException.class, () ->
                    entregaService.replanificarEntregaPorDonacion(
                            idDonacion,
                            new ReplanificarRequestDto(true, "Intento ilegal de anular entrega completada")
                    )
            );
        }

        @Test
        @DisplayName("Idempotencia: Confirmación repetida no duplica transiciones ni eventos AMQP")
        void testConfirmacionDuplicadaEsIdempotente() {
            entregaService.registrarArriboSedePorDonacion(idDonacion, "En puerta de sede");
            ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                    List.of("http://foto.jpg"), "Receptor", "123", null, idEntidad
            );

            // Primera confirmación
            Entrega primera = entregaService.confirmarEntregaPorDonacion(idDonacion, request);
            assertEquals(Entrega.EstadoEntrega.ENTREGADA, primera.getEstado());
            verify(eventPublisher, times(1)).publicarEntregaConfirmada(any());

            // Segunda confirmación repetida
            Entrega segunda = entregaService.confirmarEntregaPorDonacion(idDonacion, request);
            assertEquals(Entrega.EstadoEntrega.ENTREGADA, segunda.getEstado());
            // No debe haberse vuelto a emitir el evento (idempotencia activa)
            verify(eventPublisher, times(1)).publicarEntregaConfirmada(any());
        }
    }

    @Nested
    @DisplayName("Invariantes de Estado de la Ruta y el Camión")
    class InvariantesRutaYCamion {

        @Test
        @DisplayName("No se puede iniciar una ruta que ya está EN_CURSO o FINALIZADA")
        void testIniciarRutaEnEstadoInvalidoLanzaIllegalStateException() {
            Ruta ruta = new Ruta("CAM-01", "AE789XY", LocalDate.now());
            ruta.setId("RUTA-YA-INICIADA");
            ruta.iniciarRuta(); // Pasa a EN_CURSO
            rutaDatabase.put(ruta.getId(), ruta);

            assertThrows(IllegalStateException.class, () ->
                    recorridoService.iniciarRuta("RUTA-YA-INICIADA")
            );

            ruta.finalizarRuta(); // Pasa a FINALIZADA
            assertThrows(IllegalStateException.class, () ->
                    recorridoService.iniciarRuta("RUTA-YA-INICIADA")
            );
        }

        @Test
        @DisplayName("No se puede finalizar una ruta que aún está PLANIFICADA sin haberla iniciado")
        void testFinalizarRutaSinHaberIniciadoLanzaIllegalStateException() {
            Ruta ruta = new Ruta("CAM-01", "AE789XY", LocalDate.now());
            ruta.setId("RUTA-PLANIFICADA");
            rutaDatabase.put(ruta.getId(), ruta);

            assertThrows(IllegalStateException.class, () ->
                    recorridoService.finalizarRuta("RUTA-PLANIFICADA")
            );
        }
    }
}
