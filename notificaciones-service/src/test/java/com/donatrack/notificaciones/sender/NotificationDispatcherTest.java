package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationDispatcherTest {

    @Mock
    private NotificationSender emailSender;

    @Mock
    private NotificationSender smsSender;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        when(emailSender.getCanal()).thenReturn(CanalNotificacion.EMAIL);
        when(smsSender.getCanal()).thenReturn(CanalNotificacion.SMS);

        dispatcher = new NotificationDispatcher(List.of(emailSender, smsSender));
    }

    @Test
    void testDespachoPorCanalEmail() {
        dispatcher.despachar(CanalNotificacion.EMAIL, "donante@mail.com", "Asunto Test", "Mensaje Test");

        verify(emailSender, times(1)).enviar("donante@mail.com", "Asunto Test", "Mensaje Test");
        verify(smsSender, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void testDespachoPorCanalSms() {
        dispatcher.despachar(CanalNotificacion.SMS, "+54 11 1234-5678", "Asunto SMS", "Texto SMS");

        verify(smsSender, times(1)).enviar("+54 11 1234-5678", "Asunto SMS", "Texto SMS");
        verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void testSoportaCanal() {
        assertTrue(dispatcher.soportaCanal(CanalNotificacion.EMAIL));
        assertTrue(dispatcher.soportaCanal(CanalNotificacion.SMS));
    }
}
