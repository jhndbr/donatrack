package com.donatrack.notificaciones.consumer;

import com.donatrack.common.events.*;
import com.donatrack.notificaciones.sender.EmailNotificationSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificacionesConsumersTest {

    @Mock
    private EmailNotificationSender emailSender;

    private ObjectMapper objectMapper;
    private DonacionNotificacionesConsumer donacionConsumer;
    private LogisticaNotificacionesConsumer logisticaConsumer;
    private IncentivosNotificacionesConsumer incentivosConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        donacionConsumer = new DonacionNotificacionesConsumer(emailSender, objectMapper);
        logisticaConsumer = new LogisticaNotificacionesConsumer(emailSender);
        incentivosConsumer = new IncentivosNotificacionesConsumer(emailSender);
    }

    @Test
    void testDonacionAsignadaNotificaADonanteYEntidad() {
        DonacionAsignadaEvent evento = new DonacionAsignadaEvent(
                1L, "Lácteos", 50.0, "litros",
                10L, "Comedor Los Chicos", "comedor@mail.com",
                20L, "donante@mail.com", "María Gómez"
        );

        donacionConsumer.recibirDonacionAsignada(evento);

        verify(emailSender, times(1)).enviar(eq("donante@mail.com"), anyString(), contains("María Gómez"));
        verify(emailSender, times(1)).enviar(eq("comedor@mail.com"), anyString(), contains("Comedor Los Chicos"));
    }

    @Test
    void testDonanteInactivoNotificaADonante() {
        DonanteInactivoEvent evento = new DonanteInactivoEvent(
                20L, "María Gómez", "donante@mail.com", 25L
        );

        donacionConsumer.recibirDonanteInactivo(evento);

        verify(emailSender, times(1)).enviar(eq("donante@mail.com"), anyString(), contains("25 días"));
    }

    @Test
    void testRutaIniciadaNotificaDestinatarios() {
        RutaIniciadaEvent evento = new RutaIniciadaEvent(
                "RUTA-01", "AE789XY", List.of(1L),
                List.of("donante@mail.com", "entidad@mail.com"),
                "https://donatrack.org/tracking/rutas/RUTA-01"
        );

        logisticaConsumer.recibirRutaIniciada(evento);

        verify(emailSender, times(1)).enviar(eq("donante@mail.com"), anyString(), contains("AE789XY"));
        verify(emailSender, times(1)).enviar(eq("entidad@mail.com"), anyString(), contains("AE789XY"));
    }

    @Test
    void testEntregaConfirmadaNotificaConComprobante() {
        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                "ENT-01", 100L, 10L, "AE789XY",
                "https://donatrack.org/comprobantes/ENT-01",
                "donante@mail.com", "entidad@mail.com"
        );

        logisticaConsumer.recibirEntregaConfirmada(evento);

        verify(emailSender, times(1)).enviar(eq("donante@mail.com"), anyString(), contains("https://donatrack.org/comprobantes/ENT-01"));
        verify(emailSender, times(1)).enviar(eq("entidad@mail.com"), anyString(), contains("https://donatrack.org/comprobantes/ENT-01"));
    }

    @Test
    void testEntregaFallidaNotificaConMotivo() {
        EntregaFallidaEvent evento = new EntregaFallidaEvent(
                "ENT-01", 100L, 10L, "Camino inundado", true,
                "donante@mail.com", "entidad@mail.com"
        );

        logisticaConsumer.recibirEntregaFallida(evento);

        verify(emailSender, times(1)).enviar(eq("donante@mail.com"), anyString(), contains("Camino inundado"));
        verify(emailSender, times(1)).enviar(eq("entidad@mail.com"), anyString(), contains("Camino inundado"));
    }

    @Test
    void testMisionCumplidaNotificaInsignia() {
        MisionCumplidaEvent evento = new MisionCumplidaEvent(
                42L, "Lucía Benítez", "lucia@mail.com", "RACHA",
                "Insignia Inicial", "Primera donación", "COLABORADOR"
        );

        incentivosConsumer.recibirMisionCumplida(evento);

        verify(emailSender, times(1)).enviar(eq("lucia@mail.com"), anyString(), contains("Insignia Inicial"));
    }

    @Test
    void testCategoriaAscendidaNotificaNuevoNivel() {
        CategoriaAscendidaEvent evento = new CategoriaAscendidaEvent(
                42L, "Lucía Benítez", "lucia@mail.com", "COLABORADOR", "SOSTENEDOR"
        );

        incentivosConsumer.recibirCategoriaAscendida(evento);

        verify(emailSender, times(1)).enviar(eq("lucia@mail.com"), anyString(), contains("SOSTENEDOR"));
    }
}
