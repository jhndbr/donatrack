package com.donatrack.frontend.controller;

import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Map;

/**
 * Inyecta de forma global en todas las vistas Thymeleaf el contador de notificaciones no leídas
 * (indicador numérico rojo en la barra de navegación superior) según el rol y sesión activa.
 * Resuelve de forma robusta los correos principales registrados tanto para Donantes como para
 * Entidades Beneficiarias y Choferes, garantizando que el indicador aparezca en todas las pantallas.
 */
@ControllerAdvice
public class GlobalNotificationsAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalNotificationsAdvice.class);
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

            String destinatario = user.getEmail();

            if (user.isAdmin()) {
                // El administrador visualiza el total global de notificaciones no leídas en el sistema
                destinatario = null;
            } else if (user.isDonante()) {
                // Resolver el correo principal registrado del donante
                String donanteMail = null;
                if (user.getDonanteId() != null) {
                    Map<String, Object> donante = apiClient.obtenerDonante(user.getDonanteId());
                    if (donante != null && donante.get("emailPrincipal") != null) {
                        donanteMail = String.valueOf(donante.get("emailPrincipal"));
                    }
                } else {
                    // Si donanteId no estaba en la sesión, resolverlo por username / email / DNI
                    try {
                        List<Map<String, Object>> donantes = apiClient.listarDonantes();
                        if (donantes != null) {
                            for (Map<String, Object> d : donantes) {
                                String dni = d.get("dni") != null ? d.get("dni").toString() : "";
                                String email = d.get("emailPrincipal") != null ? d.get("emailPrincipal").toString() : "";
                                if ((!dni.isBlank() && dni.equalsIgnoreCase(user.getUsername())) ||
                                        (!email.isBlank() && email.equalsIgnoreCase(user.getEmail()))) {
                                    Object idObj = d.get("id");
                                    if (idObj instanceof Number num) {
                                        user.setDonanteId(num.longValue());
                                        donanteMail = email;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (donanteMail != null && !donanteMail.isBlank()) {
                    destinatario = donanteMail;
                }
            } else if (user.isBeneficiaria()) {
                // Resolver el correo institucional registrado de la entidad
                if (user.getEntidadId() != null) {
                    Map<String, Object> ent = apiClient.obtenerEntidad(user.getEntidadId());
                    if (ent != null && !ent.isEmpty()) {
                        String entMail = null;
                        if (ent.get("emailPrincipal") != null && !String.valueOf(ent.get("emailPrincipal")).isBlank()) {
                            entMail = String.valueOf(ent.get("emailPrincipal"));
                        } else if (ent.get("emailsRepresentantes") instanceof List<?> list && !list.isEmpty()) {
                            entMail = String.valueOf(list.get(0));
                        } else if (ent.get("email") != null) {
                            entMail = String.valueOf(ent.get("email"));
                        } else if (ent.get("contacto") instanceof Map<?, ?> c && c.get("email") != null) {
                            entMail = String.valueOf(c.get("email"));
                        }
                        if (entMail != null && !entMail.isBlank()) {
                            destinatario = entMail;
                        }
                    }
                }
            }

            // Consultar conteo de no leídas para el destinatario resuelto
            long noLeidas = apiClient.obtenerConteoNotificacionesNoLeidas(destinatario);

            // Si el conteo primario dio 0 pero user.getEmail() es diferente a destinatario, chequear también user.getEmail()
            if (destinatario != null && user.getEmail() != null && !user.getEmail().equalsIgnoreCase(destinatario)) {
                long noLeidasUser = apiClient.obtenerConteoNotificacionesNoLeidas(user.getEmail());
                if (noLeidasUser > noLeidas) {
                    noLeidas = noLeidasUser;
                }
            }

            log.debug("populateGlobalNotificationCounters: role={}, email={}, destinatario={}, noLeidas={}",
                    user.getRole(), user.getEmail(), destinatario, noLeidas);
            model.addAttribute("notificacionesNoLeidasCount", noLeidas);
        } catch (Exception e) {
            log.debug("Error al calcular conteo global de notificaciones: {}", e.getMessage());
            model.addAttribute("notificacionesNoLeidasCount", 0L);
        }
    }
}
