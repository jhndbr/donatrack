package com.donatrack.logistica.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.logistica.client.DonacionesServiceClient;
import com.donatrack.logistica.domain.Camion;
import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.domain.Parada;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.dto.ComprobanteEntregaDto;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.EntregaFallidaRequestDto;
import com.donatrack.logistica.dto.ReplanificarRequestDto;
import com.donatrack.logistica.exception.ConflictException;
import com.donatrack.logistica.publisher.LogisticaEventPublisher;
import com.donatrack.logistica.repository.CamionRepository;
import com.donatrack.logistica.repository.EntregaRepository;
import com.donatrack.logistica.repository.RutaRepository;
import com.donatrack.logistica.repository.UbicacionGPSRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Suite de pruebas exhaustiva para el tramo final del flujo logístico:
 * Desde el momento en que el chofer arriba a la sede de la entidad benéfica (EN_PUERTA_SEDE),
 * hasta la recepción física con fotos y comprobante (ENTREGADA),
 * el flujo alternativo de rechazo o comedor cerrado (NO_RECIBIDA),
 * el reingreso administrativo a depósito (CU-45) y la finalización de la hoja de ruta.
 */
@ExtendWith(MockitoExtension.class)
public class FlujoLlegadaRecepcionEntregaTest {

    @Mock
    private RutaRepository rutaRepository;

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private CamionRepository camionRepository;

    @Mock
    private UbicacionGPSRepository gpsRepository;

    @Mock
    private DonacionesServiceClient donacionesServiceClient;

    @Mock
    private LogisticaEventPublisher eventPublisher;

    private RecorridoServiceImpl recorridoService;
    private EntregaServiceImpl entregaService;
    private TelemetriaServiceImpl telemetriaService;

    private Ruta rutaActiva;
    private Entrega entregaEnTraslado;
    private final Long ID_DONACION = 1001L;
    private final Long ID_ENTIDAD = 20L;
    private final Long ID_DONANTE = 55L;
    private final String PATENTE = "AF123JK";

    private final Map<String, Ruta> rutaDb = new HashMap<>();
    private final Map<String, Entrega> entregaDb = new HashMap<>();

    @BeforeEach
    void setUp() {
        rutaDb.clear();
        entregaDb.clear();

        // 1. Configurar servicios bajo prueba
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

        ReflectionTestUtils.setField(entregaService, "rutaRepository", rutaRepository);
        ReflectionTestUtils.setField(entregaService, "telemetriaService", telemetriaService);

        // 2. Mocks de repositorios simulando persistencia en memoria
        lenient().when(rutaRepository.save(any(Ruta.class))).thenAnswer(inv -> {
            Ruta r = inv.getArgument(0);
            if (r.getId() == null) r.setId("RUTA-TEST-01");
            rutaDb.put(r.getId(), r);
            return r;
        });
        lenient().when(rutaRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(rutaDb.get(inv.getArgument(0))));

        lenient().when(entregaRepository.save(any(Entrega.class))).thenAnswer(inv -> {
            Entrega e = inv.getArgument(0);
            if (e.getId() == null) e.setId("ENT-TEST-01");
            entregaDb.put(e.getId(), e);
            return e;
        });
        lenient().when(entregaRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(entregaDb.get(inv.getArgument(0))));
        lenient().when(entregaRepository.findByIdDonacion(eq(ID_DONACION))).thenAnswer(inv ->
                entregaDb.values().stream().filter(e -> ID_DONACION.equals(e.getIdDonacion())).findFirst()
        );

        // 3. Estado inicial: Ruta iniciada (EN_CURSO) y Donación en camión (EN_TRASLADO)
        rutaActiva = new Ruta("CAM-02", PATENTE, LocalDate.now());
        rutaActiva.setId("RUTA-TEST-01");
        rutaActiva.iniciarRuta();

        Parada paradaSede = new Parada(1, Parada.TipoParada.ENTREGA, ID_ENTIDAD, "Comedor Los Chicos Felices", "Av. San Martín 450", -34.6037, -58.3816, ID_DONACION);
        paradaSede.setDetalleDonacion("25 kg de Harina y Alimentos Secos");
        paradaSede.marcarEnTraslado();
        rutaActiva.agregarParada(paradaSede);
        rutaDb.put(rutaActiva.getId(), rutaActiva);

        entregaEnTraslado = new Entrega(
                rutaActiva.getId(),
                ID_DONACION,
                ID_ENTIDAD,
                "Comedor Los Chicos Felices",
                "comedor.chicos@ong.org",
                ID_DONANTE,
                "donante.carlos@gmail.com",
                PATENTE
        );
        entregaEnTraslado.setId("ENT-TEST-01");
        entregaEnTraslado.marcarEnTraslado();
        entregaDb.put(entregaEnTraslado.getId(), entregaEnTraslado);
    }

    @Nested
    @DisplayName("Paso 1: Chofer llega a la puerta de la sede (EN_PUERTA_SEDE)")
    class ArriboEnPuertaSede {

        @Test
        @DisplayName("El chofer marca arribo en puerta: actualiza etapa a EN_PUERTA_SEDE, sincroniza parada en ruta y posición GPS")
        void choferMarcaArriboEnPuertaExitosamente() {
            // WHEN: El chofer reporta su arribo en la puerta de la entidad
            Entrega entregaArribada = entregaService.registrarArriboSedePorDonacion(ID_DONACION, "Camión estacionado en la puerta de descarga");

            // THEN:
            assertNotNull(entregaArribada);
            assertEquals("EN_PUERTA_SEDE", entregaArribada.getEtapaOperativa());
            assertTrue(entregaArribada.isConfirmadoPorChofer());
            assertNotNull(entregaArribada.getFechaArriboSede());
            assertEquals("Camión estacionado en la puerta de descarga", entregaArribada.getObservacionesRecepcion());

            // La parada en la hoja de ruta debe reflejar EN_PUERTA_SEDE
            Ruta rutaActualizada = rutaDb.get(rutaActiva.getId());
            Parada p = rutaActualizada.getParadas().get(0);
            assertEquals("EN_PUERTA_SEDE", p.getEstadoParada());
            assertFalse(p.isCompletada());

            // Emite evento AMQP hacia RabbitMQ para alertar a la Entidad Beneficiaria
            verify(eventPublisher, times(1)).publicarCamionEnPuerta(any(com.donatrack.common.events.CamionEnPuertaEvent.class));
        }
    }

    @Nested
    @DisplayName("Regla de Negocio: Bloqueo de confirmación previa al arribo del camión")
    class BloqueoConfirmacionSinArribo {

        @Test
        @DisplayName("La entidad beneficiaria no puede confirmar la recepción si el chofer aún no ha notificado arribo en puerta (EN_PUERTA_SEDE)")
        void rechazarConfirmacionDeEntidadSiCamionAunNoEstaEnPuerta() {
            // GIVEN: La entrega se encuentra en camino (EN_TRASLADO / EN_TRANSITO), pero el chofer NO ha marcado 'En Puerta'
            ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                    List.of("https://res.cloudinary.com/donatrack/entrega.jpg"),
                    "Laura Fernández",
                    "32456789",
                    null,
                    ID_ENTIDAD
            );

            // WHEN & THEN: Intento de confirmar por la entidad lanza ConflictException
            ConflictException ex = assertThrows(ConflictException.class, () ->
                    entregaService.confirmarEntregaPorDonacion(ID_DONACION, request)
            );
            assertTrue(ex.getMessage().contains("aún no ha sido notificada en puerta por el chofer"));
            assertTrue(ex.getMessage().contains("físicamente en puerta de la sede"));
        }
    }

    @Nested
    @DisplayName("Paso 2A (Camino Exitoso): Inspección y Recepción con Fotografías por la Entidad")
    class RecepcionExitosaEntidad {

        @BeforeEach
        void prepararArriboPrevio() {
            entregaService.registrarArriboSedePorDonacion(ID_DONACION, "En puerta de sede");
        }

        @Test
        @DisplayName("Recepción confirmada con fotos y datos del receptor: pasa a ENTREGADA, sincroniza donaciones y emite comprobante")
        void recepcionConfirmadaConFotosYDatosReceptor() {
            // GIVEN: Formulario de recepción completado por la entidad beneficiaria
            ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                    List.of("https://res.cloudinary.com/donatrack/entrega-1001.jpg"),
                    "Laura Fernández (Coordinadora)",
                    "32456789",
                    "https://res.cloudinary.com/donatrack/firma-laura.png",
                    ID_ENTIDAD
            );

            // WHEN: Se confirma la recepción
            Entrega entregaFinal = entregaService.confirmarEntregaPorDonacion(ID_DONACION, request);

            // THEN:
            assertEquals(Entrega.EstadoEntrega.ENTREGADA, entregaFinal.getEstado());
            assertEquals("RECEPCION_CONFIRMADA", entregaFinal.getEtapaOperativa());
            assertTrue(entregaFinal.isConfirmadoPorEntidad());
            assertEquals("Laura Fernández (Coordinadora)", entregaFinal.getReceptorNombre());
            assertEquals("32456789", entregaFinal.getReceptorDni());
            assertEquals(1, entregaFinal.getFotosConfirmacionUrls().size());

            // Parada en la hoja de ruta pasa a completada y ENTREGADA
            Ruta ruta = rutaDb.get(rutaActiva.getId());
            Parada p = ruta.getParadas().get(0);
            assertTrue(p.isCompletada());
            assertEquals("ENTREGADA", p.getEstadoParada());

            // Sincronización con donaciones-service
            verify(donacionesServiceClient, times(1)).actualizarEstadoDonacion(
                    eq(ID_DONACION),
                    eq(EstadoDonacion.ENTREGADA),
                    contains("Recepción confirmada con fotografías en sede por Laura Fernández"),
                    eq("LOGISTICA_SERVICE"),
                    eq("https://res.cloudinary.com/donatrack/entrega-1001.jpg")
            );

            // Emisión de evento AMQP hacia RabbitMQ para notificaciones e incentivos
            ArgumentCaptor<EntregaConfirmadaEvent> eventCaptor = ArgumentCaptor.forClass(EntregaConfirmadaEvent.class);
            verify(eventPublisher, times(1)).publicarEntregaConfirmada(eventCaptor.capture());
            EntregaConfirmadaEvent evento = eventCaptor.getValue();
            assertEquals(ID_DONACION, evento.getIdDonacion());
            assertEquals(ID_ENTIDAD, evento.getIdEntidadBeneficiaria());
            assertEquals(PATENTE, evento.getPatenteCamion());
            assertEquals("donante.carlos@gmail.com", evento.getEmailDonante());

            // Comprobante formal generado
            ComprobanteEntregaDto comprobante = entregaService.obtenerComprobantePorDonacion(ID_DONACION);
            assertNotNull(comprobante);
            assertEquals(ID_DONACION, comprobante.idDonacion());
            assertEquals("Comedor Los Chicos Felices", comprobante.nombreEntidad());
            assertEquals(PATENTE, comprobante.patenteCamion());
            assertEquals("Laura Fernández (Coordinadora)", comprobante.receptorNombre());
            assertEquals("ENTREGADA", comprobante.estado());
        }

        @Test
        @DisplayName("Seguridad: Una entidad diferente no puede confirmar una donación asignada a otra entidad")
        void rechazarConfirmacionDeEntidadNoAutorizada() {
            Long otraEntidadId = 999L;
            ConfirmarEntregaRequestDto requestAjeno = new ConfirmarEntregaRequestDto(
                    List.of("https://res.cloudinary.com/donatrack/foto.jpg"),
                    "Intruso",
                    "00000000",
                    null,
                    otraEntidadId
            );

            ConflictException ex = assertThrows(ConflictException.class, () ->
                    entregaService.confirmarEntregaPorDonacion(ID_DONACION, requestAjeno)
            );
            assertTrue(ex.getMessage().contains("no está autorizada"));
        }

        @Test
        @DisplayName("Validación: No se puede confirmar sin adjuntar fotografía de la recepción (CU-43)")
        void rechazarConfirmacionSinFotografia() {
            ConfirmarEntregaRequestDto requestSinFoto = new ConfirmarEntregaRequestDto(
                    List.of(),
                    "Laura Fernández",
                    "32456789",
                    null,
                    ID_ENTIDAD
            );

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    entregaService.confirmarEntregaPorDonacion(ID_DONACION, requestSinFoto)
            );
            assertTrue(ex.getMessage().contains("obligatorio adjuntar al menos una fotografía"));
        }

        @Test
        @DisplayName("Idempotencia: Confirmar dos veces consecutivas no duplica eventos ni altera el estado")
        void confirmacionIdempotenteNoDuplicaEventos() {
            ConfirmarEntregaRequestDto request = new ConfirmarEntregaRequestDto(
                    List.of("https://res.cloudinary.com/donatrack/entrega.jpg"),
                    "Laura Fernández",
                    "32456789",
                    null,
                    ID_ENTIDAD
            );

            // Primera confirmación
            entregaService.confirmarEntregaPorDonacion(ID_DONACION, request);
            // Segunda confirmación (ej. doble click o reintento de red)
            Entrega segunda = entregaService.confirmarEntregaPorDonacion(ID_DONACION, request);

            assertEquals(Entrega.EstadoEntrega.ENTREGADA, segunda.getEstado());
            verify(eventPublisher, times(1)).publicarEntregaConfirmada(any(EntregaConfirmadaEvent.class));
        }
    }

    @Nested
    @DisplayName("Paso 2B (Camino Fallido): Rechazo en Sede / Comedor Cerrado (CU-44)")
    class RechazoOEntregaFallida {

        @BeforeEach
        void prepararArriboPrevio() {
            entregaService.registrarArriboSedePorDonacion(ID_DONACION, "En puerta");
        }

        @Test
        @DisplayName("Registro de entrega no recibida por sede cerrada: pasa a NO_RECIBIDA, parada FALLIDA y publica entrega.fallida")
        void registrarEntregaNoRecibidaConMotivo() {
            EntregaFallidaRequestDto request = new EntregaFallidaRequestDto(
                    "Sede cerrada tras 30 min de espera reglamentaria sin receptor presente",
                    true
            );

            Entrega entregaFallida = entregaService.registrarEntregaFallidaPorDonacion(ID_DONACION, request);

            assertEquals(Entrega.EstadoEntrega.NO_RECIBIDA, entregaFallida.getEstado());
            assertEquals("INCIDENCIA_REGISTRADA", entregaFallida.getEtapaOperativa());
            assertEquals("Sede cerrada tras 30 min de espera reglamentaria sin receptor presente", entregaFallida.getMotivoFalla());
            assertTrue(entregaFallida.isReplanificable());

            // Parada en la ruta queda como FALLIDA
            Ruta ruta = rutaDb.get(rutaActiva.getId());
            Parada p = ruta.getParadas().get(0);
            assertFalse(p.isCompletada());
            assertEquals("FALLIDA", p.getEstadoParada());
            assertEquals("Sede cerrada tras 30 min de espera reglamentaria sin receptor presente", p.getMotivoFalla());

            // Sincronización en donaciones a ENTREGA_FALLIDA
            verify(donacionesServiceClient, times(1)).actualizarEstadoDonacion(
                    eq(ID_DONACION),
                    eq(EstadoDonacion.ENTREGA_FALLIDA),
                    contains("Sede cerrada"),
                    eq("CHOFER")
            );

            // Emisión de evento AMQP entrega.fallida hacia RabbitMQ
            ArgumentCaptor<EntregaFallidaEvent> captor = ArgumentCaptor.forClass(EntregaFallidaEvent.class);
            verify(eventPublisher, times(1)).publicarEntregaFallida(captor.capture());
            EntregaFallidaEvent evento = captor.getValue();
            assertEquals(ID_DONACION, evento.getIdDonacion());
            assertTrue(evento.isReplanificable());
        }

        @Test
        @DisplayName("Validación: No se puede registrar falla con motivo en blanco")
        void rechazarFallaConMotivoVacio() {
            EntregaFallidaRequestDto request = new EntregaFallidaRequestDto("   ", true);
            assertThrows(IllegalArgumentException.class, () ->
                    entregaService.registrarEntregaFallidaPorDonacion(ID_DONACION, request)
            );
        }

        @Test
        @DisplayName("Invariante: No se puede registrar como fallida una entrega que ya fue ENTREGADA")
        void noSePuedeFallarEntregaYaEntregada() {
            ConfirmarEntregaRequestDto confirmacion = new ConfirmarEntregaRequestDto(
                    List.of("https://res.cloudinary.com/donatrack/ok.jpg"),
                    "Laura",
                    "32456789",
                    null,
                    ID_ENTIDAD
            );
            entregaService.confirmarEntregaPorDonacion(ID_DONACION, confirmacion);

            EntregaFallidaRequestDto falla = new EntregaFallidaRequestDto("Comedor cerrado", true);
            assertThrows(IllegalStateException.class, () ->
                    entregaService.registrarEntregaFallidaPorDonacion(ID_DONACION, falla)
            );
        }
    }

    @Nested
    @DisplayName("Paso 3: Retorno al Depósito y Replanificación Administrativa (CU-45)")
    class ReingresoDepositoYReplanificacion {

        @BeforeEach
        void prepararEntregaFallidaPrevia() {
            entregaService.registrarArriboSedePorDonacion(ID_DONACION, "En puerta");
            entregaService.registrarEntregaFallidaPorDonacion(ID_DONACION, new EntregaFallidaRequestDto("Comedor cerrado", true));
        }

        @Test
        @DisplayName("CU-45 A1: Si los bienes NO regresaron al depósito físico, la replanificación debe ser rechazada")
        void rechazarReplanificacionSiNoRegresoAlDeposito() {
            ReplanificarRequestDto requestSinRetorno = new ReplanificarRequestDto(
                    false, // regresoAlDeposito = false
                    "Reintento de entrega al día siguiente"
            );

            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    entregaService.replanificarEntregaPorDonacion(ID_DONACION, requestSinRetorno)
            );
            assertTrue(ex.getMessage().contains("regresó físicamente al depósito"));
        }

        @Test
        @DisplayName("CU-45 Exitoso: Con retorno físico confirmado, la entrega vuelve a PENDIENTE y la donación a EN_DEPOSITO")
        void replanificacionExitosaConRetornoAlDeposito() {
            ReplanificarRequestDto request = new ReplanificarRequestDto(
                    true, // regresoAlDeposito = true
                    "Bienes descargados en depósito central en perfecto estado"
            );

            Entrega entregaReplanificada = entregaService.replanificarEntregaPorDonacion(ID_DONACION, request);

            assertEquals(Entrega.EstadoEntrega.PENDIENTE, entregaReplanificada.getEstado());
            assertEquals("PENDIENTE_RETIRO_DEPOSITO", entregaReplanificada.getEtapaOperativa());
            assertFalse(entregaReplanificada.isReplanificable());

            // Parada en la ruta vuelve a PENDIENTE
            Ruta ruta = rutaDb.get(rutaActiva.getId());
            Parada p = ruta.getParadas().get(0);
            assertFalse(p.isCompletada());
            assertEquals("PENDIENTE", p.getEstadoParada());
            assertNull(p.getMotivoFalla());

            // La donación en donaciones-service vuelve a EN_DEPOSITO para ser elegible para una nueva ruta
            verify(donacionesServiceClient, times(1)).actualizarEstadoDonacion(
                    eq(ID_DONACION),
                    eq(EstadoDonacion.EN_DEPOSITO),
                    contains("Reingreso físico al depósito"),
                    eq("ADMIN")
            );
        }
    }

    @Nested
    @DisplayName("Paso 4: Chofer Finaliza la Hoja de Ruta")
    class FinalizacionDeRuta {

        @Test
        @DisplayName("El chofer finaliza la ruta exitosamente una vez completadas las paradas")
        void choferFinalizaRutaCompleta() {
            // Entregar la parada
            entregaService.registrarArriboSedePorDonacion(ID_DONACION, "En puerta");
            entregaService.confirmarEntregaPorDonacion(ID_DONACION, new ConfirmarEntregaRequestDto(
                    List.of("https://res.cloudinary.com/donatrack/entrega.jpg"),
                    "Laura Fernández",
                    "32456789",
                    null,
                    ID_ENTIDAD
            ));

            // Finalizar la ruta
            Ruta rutaFin = recorridoService.finalizarRuta(rutaActiva.getId());

            assertEquals(Ruta.EstadoRuta.FINALIZADA, rutaFin.getEstado());
            assertNotNull(rutaFin.getFechaFin());
        }

        @Test
        @DisplayName("Invariante: No se puede finalizar una ruta que ya está FINALIZADA")
        void noSePuedeFinalizarRutaYaFinalizada() {
            recorridoService.finalizarRuta(rutaActiva.getId());
            assertThrows(IllegalStateException.class, () ->
                    recorridoService.finalizarRuta(rutaActiva.getId())
            );
        }
    }
}
