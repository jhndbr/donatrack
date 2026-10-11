package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationSendersTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Test
    void testEmailNotificationSenderExitoso() {
        EmailNotificationSender sender = new EmailNotificationSender(javaMailSender);
        assertEquals(CanalNotificacion.EMAIL, sender.getCanal());

        sender.enviar("test@donatrack.org", "Asunto Prueba", "Mensaje Prueba");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, times(1)).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertEquals("test@donatrack.org", Objects.requireNonNull(msg.getTo())[0]);
        assertEquals("Asunto Prueba", msg.getSubject());
        assertEquals("Mensaje Prueba", msg.getText());
    }

    @Test
    void testWhatsAppNotificationSender() {
        WhatsAppNotificationSender sender = new WhatsAppNotificationSender();
        assertEquals(CanalNotificacion.WHATSAPP, sender.getCanal());

        // Simulado: no debe arrojar excepción
        sender.enviar("+54 9 11 1234-5678", "Asunto WA", "Texto WA");
    }

    @Test
    void testSmsNotificationSender() {
        SmsNotificationSender sender = new SmsNotificationSender();
        assertEquals(CanalNotificacion.SMS, sender.getCanal());

        // Simulado: no debe arrojar excepción
        sender.enviar("+54 11 9876-5432", "Asunto SMS", "Texto SMS");
    }
}
