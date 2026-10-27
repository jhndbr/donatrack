package com.donatrack.notificaciones.consumer;

import com.donatrack.common.events.*;
import com.donatrack.notificaciones.service.NotificacionesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificacionesConsumersTest {

    @Mock
    private NotificacionesService notificacionesService;

    private ObjectMapper objectMapper;
    private DonacionNotificacionesConsumer donacionConsumer;
    private LogisticaNotificacionesConsumer logisticaConsumer;
    private IncentivosNotificacionesConsumer incentivosConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        donacionConsumer = new DonacionNotificacionesConsumer(notificacionesService, objectMapper);
        logisticaConsumer = new LogisticaNotificacionesConsumer(notificacionesService, objectMapper);
        incentivosConsumer = new IncentivosNotificacionesConsumer(notificacionesService, objectMapper);
    }

    @Test
    void testDonacionAsignadaDelegaEnNotificacionesService() {
        DonacionAsignadaEvent evento = new DonacionAsignadaEvent(
                1L, "Lácteos", 50.0, "litros",
                10L, "Comedor Los Chicos", "comedor@mail.com",
                20L, "donante@mail.com", "María Gómez"
        );

        donacionConsumer.recibirDonacionAsignada(evento);

        verify(notificacionesService, times(1)).notificarDonacionAsignada(any(DonacionAsignadaEvent.class));
    }

    @Test
    void testDonanteInactivoDelegaEnNotificacionesService() {
        DonanteInactivoEvent evento = new DonanteInactivoEvent(
                20L, "María Gómez", "donante@mail.com", 25L
        );

        donacionConsumer.recibirDonanteInactivo(evento);

        verify(notificacionesService, times(1)).notificarDonanteInactivo(any(DonanteInactivoEvent.class));
    }

    @Test
    void testDonanteRegistradoDelegaEnNotificacionesService() {
        DonanteRegistradoEvent evento = new DonanteRegistradoEvent(
                20L, "María Gómez", "donante@mail.com",
                com.donatrack.common.enums.CanalNotificacion.EMAIL, "donante@mail.com",
                "mgomez", "temp123", "REGISTRO_WEB"
        );

        donacionConsumer.recibirDonanteRegistrado(evento);

        verify(notificacionesService, times(1)).notificarBienvenidaDonante(any(DonanteRegistradoEvent.class));
    }

    @Test
    void testRutaIniciadaDelegaEnNotificacionesService() {
        RutaIniciadaEvent evento = new RutaIniciadaEvent(
                "RUTA-01", "AE789XY", List.of(1L),
                List.of("donante@mail.com", "entidad@mail.com"),
                "https://donatrack.org/tracking/rutas/RUTA-01"
        );

        logisticaConsumer.recibirRutaIniciada(evento);

        verify(notificacionesService, times(1)).notificarRutaIniciada(any(RutaIniciadaEvent.class));
    }

    @Test
    void testEntregaConfirmadaDelegaEnNotificacionesService() {
        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                "ENT-01", 100L, 10L, "AE789XY",
                "https://donatrack.org/comprobantes/ENT-01",
                "donante@mail.com", "entidad@mail.com"
        );

        logisticaConsumer.recibirEntregaConfirmada(evento);

        verify(notificacionesService, times(1)).notificarEntregaConfirmada(any(EntregaConfirmadaEvent.class));
    }

    @Test
    void testEntregaFallidaDelegaEnNotificacionesService() {
        EntregaFallidaEvent evento = new EntregaFallidaEvent(
                "ENT-01", 100L, 10L, "Camino inundado", true,
                "donante@mail.com", "entidad@mail.com"
        );

        logisticaConsumer.recibirEntregaFallida(evento);

        verify(notificacionesService, times(1)).notificarEntregaFallida(any(EntregaFallidaEvent.class));
    }

    @Test
    void testMisionCumplidaDelegaEnNotificacionesService() {
        MisionCumplidaEvent evento = new MisionCumplidaEvent(
                42L, "Lucía Benítez", "lucia@mail.com", "RACHA",
                "Insignia Inicial", "Primera donación", "COLABORADOR"
        );

        incentivosConsumer.recibirMisionCumplida(evento);

        verify(notificacionesService, times(1)).notificarMisionCumplida(any(MisionCumplidaEvent.class));
    }

    @Test
    void testCategoriaAscendidaDelegaEnNotificacionesService() {
        CategoriaAscendidaEvent evento = new CategoriaAscendidaEvent(
                42L, "Lucía Benítez", "lucia@mail.com", "COLABORADOR", "SOSTENEDOR"
        );

        incentivosConsumer.recibirCategoriaAscendida(evento);

        verify(notificacionesService, times(1)).notificarCategoriaAscendida(any(CategoriaAscendidaEvent.class));
    }

    @Test
    void testCamionEnPuertaDelegaEnNotificacionesService() {
        CamionEnPuertaEvent evento = new CamionEnPuertaEvent(
                "ENT-01", 100L, 10L, "Comedor Los Chicos", "comedor@mail.com",
                "AE789XY", "Estacionado en puerta", "http://localhost:8086/beneficiaria/confirmar?donacionId=100"
        );

        logisticaConsumer.recibirCamionEnPuerta(evento);

        verify(notificacionesService, times(1)).notificarCamionEnPuerta(any(CamionEnPuertaEvent.class));
    }
}
