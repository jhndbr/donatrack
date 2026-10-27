package com.donatrack.notificaciones.service;

import com.donatrack.common.enums.CanalNotificacion;
import com.donatrack.common.events.*;
import com.donatrack.notificaciones.domain.AuditoriaNotificacion;
import com.donatrack.notificaciones.domain.EstadoNotificacion;
import com.donatrack.notificaciones.domain.RegistroEventoNotificacion;
import com.donatrack.notificaciones.repository.AuditoriaNotificacionRepository;
import com.donatrack.notificaciones.repository.RegistroEventoNotificacionRepository;
import com.donatrack.notificaciones.sender.NotificationDispatcher;
import com.donatrack.notificaciones.template.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificacionesServiceTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @Spy
    private NotificationTemplateService templateService = new NotificationTemplateService();

    @Mock
    private AuditoriaNotificacionRepository auditoriaRepo;

    @Mock
    private RegistroEventoNotificacionRepository eventoRepo;

    @InjectMocks
    private NotificacionesService notificacionesService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificacionesService, "adminEmail", "admin@donatrack.org");
    }

    @Test
    void testNotificarDonacionAsignadaGeneraHtmlYAudita() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), anyString())).thenReturn(true);

        DonacionAsignadaEvent evento = new DonacionAsignadaEvent(
                100L, "Alimentos", 25.0, "kg",
                10L, "Comedor Sol", "comedor@mail.com",
                42L, "donante@mail.com", "Lucía Benítez"
        );

        notificacionesService.notificarDonacionAsignada(evento);

        // Debe despachar 2 correos: al donante y a la entidad
        verify(dispatcher, times(2)).despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), contains("DonaTrack"));
        verify(auditoriaRepo, times(2)).save(any(AuditoriaNotificacion.class));
        verify(eventoRepo, times(2)).save(any(RegistroEventoNotificacion.class));
    }

    @Test
    void testIdempotenciaDescarteDuplicado() {
        String clave = "notif-test-123";
        when(eventoRepo.existsByClaveEvento(clave)).thenReturn(true);

        boolean resultado = notificacionesService.despacharConAuditoria(
                clave, "TEST_TIPO", CanalNotificacion.EMAIL, "donante@mail.com", "Asunto", "Mensaje"
        );

        assertFalse(resultado);
        verify(dispatcher, never()).despachar(any(), any(), any(), any());

        ArgumentCaptor<AuditoriaNotificacion> captor = ArgumentCaptor.forClass(AuditoriaNotificacion.class);
        verify(auditoriaRepo, times(1)).save(captor.capture());
        assertEquals(EstadoNotificacion.DUPLICADO_IGNORADO, captor.getValue().getEstado());
    }

    @Test
    void testNotificarRutaIniciadaConEnlaceAMapa() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), anyString())).thenReturn(true);

        RutaIniciadaEvent evento = new RutaIniciadaEvent(
                "RUTA-01", "AE789XY", List.of(100L),
                List.of("donante@mail.com", "entidad@mail.com"),
                "https://donatrack.org/tracking/rutas/RUTA-01"
        );

        notificacionesService.notificarRutaIniciada(evento);

        verify(dispatcher, times(2)).despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), contains("AE789XY"));
    }

    @Test
    void testNotificarEntregaConfirmadaConComprobanteYPatenteCamion() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), anyString())).thenReturn(true);

        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                "ENT-01", 100L, 42L, 10L, "AF123CD",
                "http://localhost:8086/beneficiaria/notificaciones",
                "donante@mail.com", "entidad@mail.com",
                CanalNotificacion.EMAIL, "donante@mail.com"
        );

        notificacionesService.notificarEntregaConfirmada(evento);

        // Despacha confirmación al donante y a la entidad
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("donante@mail.com"), contains("Donación entregada"), contains("AF123CD"));
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("entidad@mail.com"), contains("Donación entregada"), contains("AF123CD"));
        verify(auditoriaRepo, times(2)).save(any(AuditoriaNotificacion.class));
    }

    @Test
    void testNotificarCamionEnPuertaEnviaAlertaAEntidad() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), anyString())).thenReturn(true);

        com.donatrack.common.events.CamionEnPuertaEvent evento = new com.donatrack.common.events.CamionEnPuertaEvent(
                "ENT-01", 100L, 10L, "Comedor Los Chicos", "comedor@mail.com",
                "AF123CD", "Estacionado en puerta", "http://localhost:8086/beneficiaria/confirmar?donacionId=100"
        );

        notificacionesService.notificarCamionEnPuerta(evento);

        // Despacha alerta a la entidad con enlace de confirmación directa
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("comedor@mail.com"), contains("camión ha llegado a la puerta"), contains("AF123CD"));
        verify(auditoriaRepo, times(1)).save(any(AuditoriaNotificacion.class));
        verify(eventoRepo, times(1)).save(any(RegistroEventoNotificacion.class));
    }

    @Test
    void testNotificarEntregaFallidaEnviaAlertaAAdministrador() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), anyString())).thenReturn(true);

        EntregaFallidaEvent evento = new EntregaFallidaEvent(
                "ENT-01", 100L, 10L, "Camino anegado por tormenta", true,
                "donante@mail.com", "entidad@mail.com"
        );

        notificacionesService.notificarEntregaFallida(evento);

        // 3 correos: donante, entidad y administrador
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("donante@mail.com"), anyString(), anyString());
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("entidad@mail.com"), anyString(), anyString());
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("admin@donatrack.org"), contains("ALERTA OPERATIVA"), contains("Camino anegado por tormenta"));
    }

    @Test
    void testNotificarDonanteInactivoMensajePersuasivo() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), anyString())).thenReturn(true);

        DonanteInactivoEvent evento = new DonanteInactivoEvent(
                42L, "Lucía", "lucia@mail.com", 25L
        );

        notificacionesService.notificarDonanteInactivo(evento);

        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("lucia@mail.com"), contains("Te extrañamos"), contains("25 días"));
    }

    @Test
    void testNotificarMisionCumplidaYCategoriaAscendida() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(eq(CanalNotificacion.EMAIL), anyString(), anyString(), anyString())).thenReturn(true);

        MisionCumplidaEvent mision = new MisionCumplidaEvent(
                42L, "Lucía", "lucia@mail.com", "RACHA",
                "Insignia Solidaria", "Completar 3 meses seguidos", "COLABORADOR"
        );
        notificacionesService.notificarMisionCumplida(mision);

        CategoriaAscendidaEvent cat = new CategoriaAscendidaEvent(
                42L, "Lucía", "lucia@mail.com", "COLABORADOR", "SOSTENEDOR"
        );
        notificacionesService.notificarCategoriaAscendida(cat);

        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("lucia@mail.com"), contains("insignia"), contains("Insignia Solidaria"));
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("lucia@mail.com"), contains("nivel"), contains("SOSTENEDOR"));
    }

    @Test
    void testNotificarBienvenidaDonante() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(any(), anyString(), anyString(), anyString())).thenReturn(true);

        DonanteRegistradoEvent evento = new DonanteRegistradoEvent(
                55L, "Carlos López", "carlos@mail.com",
                CanalNotificacion.EMAIL, "carlos@mail.com",
                "clopez", "tempPass456", "WEB"
        );

        notificacionesService.notificarBienvenidaDonante(evento);

        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("carlos@mail.com"), contains("Bienvenido/a"), contains("clopez"));
        verify(eventoRepo, times(1)).save(any(RegistroEventoNotificacion.class));
    }

    @Test
    void testNotificarDonacionAsignadaConCanalWhatsApp() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(any(), anyString(), anyString(), anyString())).thenReturn(true);

        DonacionAsignadaEvent evento = new DonacionAsignadaEvent(
                200L, "Indumentaria", "Ropa", 10.0, "unidades",
                10L, "Hogar Dulce Hogar", "hogar@mail.com",
                42L, "donante@mail.com", "Lucía Benítez",
                CanalNotificacion.WHATSAPP, "+5491199998888"
        );

        notificacionesService.notificarDonacionAsignada(evento);

        // Despacha WhatsApp al donante y EMAIL a la entidad
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.WHATSAPP), eq("+5491199998888"), anyString(), contains("Lucía Benítez"));
        verify(dispatcher, times(1)).despachar(eq(CanalNotificacion.EMAIL), eq("hogar@mail.com"), anyString(), anyString());
    }

    @Test
    void testFallaDespachoNoGuardaRegistroDeUnicidadParaPermitirReintento() {
        when(eventoRepo.existsByClaveEvento(anyString())).thenReturn(false);
        when(dispatcher.despachar(any(), anyString(), anyString(), anyString())).thenReturn(false);

        boolean resultado = notificacionesService.despacharConAuditoria(
                "clave-falla-1", "TEST_FALLA", CanalNotificacion.EMAIL, "error@mail.com", "Asunto", "Mensaje"
        );

        assertFalse(resultado);
        // Debe auditar como FALLIDO pero NO guardar en eventoRepo
        verify(auditoriaRepo, times(1)).save(any(AuditoriaNotificacion.class));
        verify(eventoRepo, never()).save(any(RegistroEventoNotificacion.class));
    }
}
