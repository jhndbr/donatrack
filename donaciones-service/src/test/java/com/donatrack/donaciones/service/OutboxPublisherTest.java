package com.donatrack.donaciones.service;

import com.donatrack.donaciones.domain.OutboxEvent;
import com.donatrack.donaciones.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxPublisher, "exchangeName", "donatrack.events.exchange");
    }

    @Test
    void testPublicarEventosPendientesExitoso() {
        OutboxEvent event1 = new OutboxEvent("Donacion", "1", "DonacionAsignadaEvent", "donacion.asignada", "{\"id\":1}");
        event1.setId(10L);

        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(event1));

        outboxPublisher.publicarEventosPendientes();

        verify(rabbitTemplate, times(1)).convertAndSend("donatrack.events.exchange", "donacion.asignada", "{\"id\":1}");
        assertEquals(OutboxEvent.OutboxStatus.PUBLISHED, event1.getStatus());
        verify(outboxEventRepository, times(1)).save(event1);
    }

    @Test
    void testPublicarEventosPendientesConFalloEnRabbitMq() {
        OutboxEvent event = new OutboxEvent("Donacion", "1", "DonacionAsignadaEvent", "donacion.asignada", "{\"id\":1}");
        event.setId(10L);

        when(outboxEventRepository.findPendingEvents()).thenReturn(List.of(event));
        doThrow(new RuntimeException("RabbitMQ connection refused")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), anyString());

        outboxPublisher.publicarEventosPendientes();

        assertEquals(1, event.getRetries());
        verify(outboxEventRepository, times(1)).save(event);
    }

    @Test
    void testSinEventosPendientesNoInteractuaConRabbitMq() {
        when(outboxEventRepository.findPendingEvents()).thenReturn(Collections.emptyList());

        outboxPublisher.publicarEventosPendientes();

        verifyNoInteractions(rabbitTemplate);
        verify(outboxEventRepository, never()).save(any());
    }
}
