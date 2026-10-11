package com.donatrack.frontend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER_KEY = "userSession";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // 1. Rutas que no requieren autenticación
        if (isPublicUri(uri)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        UserSession userSession = (session != null) ? (UserSession) session.getAttribute(SESSION_USER_KEY) : null;

        // 2. Si no hay sesión activa, redirigir a login
        if (userSession == null) {
            response.sendRedirect("/login?unauthorized=true&redirect=" + uri);
            return false;
        }

        // 3. Verificación de Roles por Ruta
        if (uri.startsWith("/admin")) {
            if (!userSession.isAdmin()) {
                response.sendRedirect("/login?denied=true");
                return false;
            }
        } else if (uri.startsWith("/donante")) {
            if (!userSession.isDonante() && !userSession.isAdmin()) {
                response.sendRedirect("/login?denied=true");
                return false;
            }
        } else if (uri.startsWith("/beneficiaria")) {
            if (!userSession.isBeneficiaria() && !userSession.isAdmin()) {
                response.sendRedirect("/login?denied=true");
                return false;
            }
        }

        return true;
    }

    private boolean isPublicUri(String uri) {
        return uri.equals("/") ||
                uri.equals("/login") ||
                uri.equals("/logout") ||
                uri.equals("/registro") ||
                uri.startsWith("/registro/") ||
                uri.equals("/comprobantes") ||
                uri.equals("/privacidad") ||
                uri.startsWith("/css/") ||
                uri.startsWith("/js/") ||
                uri.startsWith("/images/") ||
                uri.startsWith("/webjars/");
    }
}
