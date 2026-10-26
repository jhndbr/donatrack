package com.donatrack.frontend.controller;

import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Inyecta de forma global en todas las vistas Thymeleaf el contador de notificaciones no leídas
 * (indicador numérico rojo en la barra de navegación superior) según el rol y sesión activa.
 */
@ControllerAdvice
public class GlobalNotificationsAdvice {

    private final ApiClientService apiClient;

    public GlobalNotificationsAdvice(ApiClientService apiClient) {
        this.apiClient = apiClient;
    }

    @ModelAttribute
    public void populateGlobalNotificationCounters(HttpSession session, Model model) {
        if (session == null || model == null) {
            return;
        }
        try {
            Object rawUser = session.getAttribute(AuthInterceptor.SESSION_USER_KEY);
            if (!(rawUser instanceof UserSession user)) {
                return;
            }
            String destinatario;
            if (user.isDonante() || user.isBeneficiaria()) {
                destinatario = user.getEmail();
            } else if (user.isAdmin()) {
                destinatario = null; // El administrador visualiza el total global no leído en auditoría
            } else {
                destinatario = user.getEmail();
            }

            long noLeidas = apiClient.obtenerConteoNotificacionesNoLeidas(destinatario);
            model.addAttribute("notificacionesNoLeidasCount", noLeidas);
        } catch (Exception ignored) {
            model.addAttribute("notificacionesNoLeidasCount", 0L);
        }
    }
}
