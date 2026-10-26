package com.donatrack.frontend.controller;

import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
public class BuzonWebController {

    private final ApiClientService apiClient;

    public BuzonWebController(ApiClientService apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping({"/buzon", "/correos"})
    public String buzon(Model model,
                        @RequestParam(value = "query", required = false) String query,
                        @RequestParam(value = "id", required = false) String mensajeId,
                        @RequestParam(value = "verTodos", required = false, defaultValue = "false") boolean verTodos,
                        HttpSession session) {
        UserSession userSession = (session != null)
                ? (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY)
                : null;

        if (userSession == null) {
            return "redirect:/login?unauthorized=true&redirect=/buzon";
        }

        boolean isAdmin = userSession.isAdmin();
        String emailUsuario = userSession.getEmail();
        boolean filtradoPorUsuario = false;

        String searchQuery = null;
        if (!isAdmin && emailUsuario != null && !emailUsuario.isBlank()) {
            // Usuario no-admin (donante o beneficiaria): aislamiento estricto obligatorio
            filtradoPorUsuario = true;
            verTodos = false; // Jamás permitir verTodos a un no-administrador
            if (query != null && !query.trim().isBlank()) {
                searchQuery = "to:" + emailUsuario.trim() + " " + query.trim();
            } else {
                searchQuery = "to:" + emailUsuario.trim();
            }
        } else {
            // Administrador o sesión anónima
            if (query != null && !query.trim().isBlank()) {
                searchQuery = query.trim();
            }
        }

        Map<String, Object> data = apiClient.listarMensajesMailpit(searchQuery, 50);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) data.getOrDefault("messages", Collections.emptyList());

        // Para usuarios no-admin, el conteo total refleja solo SUS mensajes recibidos
        Object total = filtradoPorUsuario ? data.getOrDefault("messages_count", messages.size()) : data.getOrDefault("total", 0);
        Object unread = data.getOrDefault("unread", 0);

        Map<String, Object> mensajeSeleccionado = null;
        if (mensajeId != null && !mensajeId.isBlank()) {
            Map<String, Object> candidate = apiClient.obtenerMensajeMailpit(mensajeId);
            if (isAdmin || mensajePerteneceAUsuario(candidate, emailUsuario)) {
                mensajeSeleccionado = candidate;
            }
        } else if (!messages.isEmpty()) {
            Object firstId = messages.get(0).get("ID");
            if (firstId != null) {
                mensajeSeleccionado = apiClient.obtenerMensajeMailpit(firstId.toString());
            }
        }

        model.addAttribute("messages", messages);
        model.addAttribute("total", total);
        model.addAttribute("unread", unread);
        model.addAttribute("query", query != null ? query : "");
        model.addAttribute("filtradoPorUsuario", filtradoPorUsuario);
        model.addAttribute("emailUsuario", emailUsuario);
        model.addAttribute("verTodos", verTodos && isAdmin);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("mensajeSeleccionado", mensajeSeleccionado);
        model.addAttribute("mailpitUrl", apiClient.getMailpitUrl());

        return "buzon";
    }

    @GetMapping("/buzon/raw/{id}")
    @ResponseBody
    public ResponseEntity<String> verHtmlMensaje(@PathVariable("id") String id, HttpSession session) {
        UserSession userSession = (session != null)
                ? (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY)
                : null;

        Map<String, Object> msg = apiClient.obtenerMensajeMailpit(id);
        if (msg == null || msg.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (userSession == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                    .body("Debe iniciar sesión para ver este correo.");
        }

        // Control de acceso por destinatario
        boolean isAdmin = userSession.isAdmin();
        if (!isAdmin) {
            String email = userSession.getEmail();
            if (!mensajePerteneceAUsuario(msg, email)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                        .body("Acceso denegado: este correo no corresponde a su cuenta.");
            }
        }

        if (msg.containsKey("HTML") && msg.get("HTML") != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
                    .body(String.valueOf(msg.get("HTML")));
        }
        String fallbackText = (msg.containsKey("Text") && msg.get("Text") != null)
                ? String.valueOf(msg.get("Text"))
                : "Mensaje sin contenido visible.";
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                .body(fallbackText);
    }

    @PostMapping("/buzon/vaciar")
    public String vaciarBuzon(HttpSession session, RedirectAttributes redirectAttrs) {
        UserSession userSession = (session != null)
                ? (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY)
                : null;

        if (userSession == null || !userSession.isAdmin()) {
            redirectAttrs.addFlashAttribute("mensajeError", "Acción no autorizada: solo los administradores pueden vaciar el buzón del servidor.");
            return "redirect:/buzon";
        }

        boolean ok = apiClient.vaciarBuzonMailpit();
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Buzón de correos vaciado correctamente.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo vaciar el buzón de Mailpit.");
        }
        return "redirect:/buzon";
    }

    @PostMapping("/buzon/{id}/eliminar")
    public String eliminarMensaje(@PathVariable("id") String id, HttpSession session, RedirectAttributes redirectAttrs) {
        UserSession userSession = (session != null)
                ? (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY)
                : null;

        boolean isAdmin = (userSession != null && userSession.isAdmin());
        if (!isAdmin) {
            if (userSession == null || userSession.getEmail() == null) {
                redirectAttrs.addFlashAttribute("mensajeError", "Acción no autorizada.");
                return "redirect:/buzon";
            }
            Map<String, Object> candidate = apiClient.obtenerMensajeMailpit(id);
            if (!mensajePerteneceAUsuario(candidate, userSession.getEmail())) {
                redirectAttrs.addFlashAttribute("mensajeError", "Acción no autorizada: no puedes eliminar correos de otras cuentas.");
                return "redirect:/buzon";
            }
        }

        boolean ok = apiClient.eliminarMensajeMailpit(id);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Mensaje eliminado del buzón.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo eliminar el mensaje.");
        }
        return "redirect:/buzon";
    }

    /**
     * Valida si el mensaje tiene como destinatario (campo To) el correo del usuario logueado.
     */
    private boolean mensajePerteneceAUsuario(Map<String, Object> msg, String emailUsuario) {
        if (msg == null || emailUsuario == null || emailUsuario.isBlank()) {
            return false;
        }
        Object toObj = msg.get("To");
        if (toObj instanceof List<?> toList) {
            for (Object item : toList) {
                if (item instanceof Map<?, ?> toMap) {
                    Object addr = toMap.get("Address");
                    if (addr != null && emailUsuario.trim().equalsIgnoreCase(addr.toString().trim())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
