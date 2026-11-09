package com.donatrack.donaciones.consumer;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.common.events.EntregaConfirmadaEvent;
import com.donatrack.common.events.EntregaFallidaEvent;
import com.donatrack.donaciones.service.DonacionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogisticaEventConsumerTest {

    @Mock
    private DonacionService donacionService;

    @InjectMocks
    private LogisticaEventConsumer consumer;

    @Test
    @DisplayName("ARCH-01 / TASK-P1-05: Sincronización asíncrona de EntregaConfirmadaEvent actualiza Donación a ENTREGADA")
    void testRecibirEntregaConfirmadaActualizaEstadoDonacion() {
        EntregaConfirmadaEvent evento = new EntregaConfirmadaEvent(
                "ENT-55", 100L, 10L, 20L, "AE123CD",
                "http://donatrack/comprobante/55.pdf",
                "donante@test.com", "entidad@test.com",
                null, "donante@test.com"
        );

        consumer.recibirEntregaConfirmada(evento);

        verify(donacionService, times(1)).actualizarEstado(
                eq(100L),
                eq(EstadoDonacion.ENTREGADA),
                anyString(),
                eq("LOGISTICA_AMQP"),
                eq("http://donatrack/comprobante/55.pdf")
        );
    }

    @Test
    @DisplayName("ARCH-01 / TASK-P1-05: Sincronización asíncrona de EntregaFallidaEvent actualiza Donación a ENTREGA_FALLIDA")
    void testRecibirEntregaFallidaActualizaEstadoDonacion() {
        EntregaFallidaEvent evento = new EntregaFallidaEvent(
                "ENT-56", 101L, 10L, 20L,
                "Dirección inaccesible", false,
                "donante@test.com", "entidad@test.com"
        );

        consumer.recibirEntregaFallida(evento);

        verify(donacionService, times(1)).actualizarEstado(
                eq(101L),
                eq(EstadoDonacion.ENTREGA_FALLIDA),
                contains("Dirección inaccesible"),
                eq("LOGISTICA_AMQP")
        );
    }
}
