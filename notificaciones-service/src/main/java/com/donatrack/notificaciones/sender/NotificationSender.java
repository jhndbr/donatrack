package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;

public interface NotificationSender {
    CanalNotificacion getCanal();
    void enviar(String destinatario, String asunto, String mensaje);
}
