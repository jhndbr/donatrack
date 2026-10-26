package com.donatrack.frontend.controller;

import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/chofer")
public class ChoferWebController {

    private final ApiClientService apiClient;

    public ChoferWebController(ApiClientService apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping
    public String panelChofer(@RequestParam(value = "rutaId", required = false) String rutaId,
                              Model model,
                              HttpSession session) {
        UserSession userSession = (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY);
        List<Map<String, Object>> rutas = apiClient.listarRutas();
        List<Map<String, Object>> camiones = apiClient.listarCamiones();

        Map<String, Object> rutaActiva = null;
        if (rutaId != null && !rutaId.isBlank()) {
            rutaActiva = apiClient.obtenerRuta(rutaId);
        } else if (!rutas.isEmpty()) {
            // Priorizar una ruta EN_CURSO, sino PLANIFICADA, sino la primera
            Optional<Map<String, Object>> enCurso = rutas.stream()
                    .filter(r -> "EN_CURSO".equals(r.get("estado")))
                    .findFirst();
            if (enCurso.isPresent()) {
                rutaActiva = enCurso.get();
            } else {
                rutaActiva = rutas.get(0);
            }
        }

        List<Map<String, Object>> entregas = Collections.emptyList();
        if (rutaActiva != null && rutaActiva.get("id") != null) {
            String activeId = rutaActiva.get("id").toString();
            entregas = apiClient.listarEntregasPorRuta(activeId);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paradas = (rutaActiva != null && rutaActiva.containsKey("paradas") && rutaActiva.get("paradas") instanceof List<?> pList)
                ? (List<Map<String, Object>>) pList
                : Collections.emptyList();

        model.addAttribute("rutas", rutas);
        model.addAttribute("camiones", camiones);
        model.addAttribute("rutaActiva", rutaActiva);
        model.addAttribute("paradas", paradas);
        model.addAttribute("entregas", entregas);
        model.addAttribute("userSession", userSession);

        return "chofer/ruta";
    }

    @PostMapping("/rutas/{id}/iniciar")
    public String iniciarRuta(@PathVariable("id") String id,
                              RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.iniciarRuta(id);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Ruta iniciada correctamente! El estado pasó a EN_CURSO y se notificó el inicio de traslado.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo iniciar la ruta " + id + ". Verifique su estado.");
        }
        return "redirect:/chofer?rutaId=" + id;
    }

    @PostMapping("/rutas/{id}/finalizar")
    public String finalizarRuta(@PathVariable("id") String id,
                                RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.finalizarRuta(id);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Ruta " + id + " finalizada con éxito! Todos los comprobantes fueron consolidados.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo finalizar la ruta " + id + ".");
        }
        return "redirect:/chofer?rutaId=" + id;
    }

    @PostMapping("/telemetria")
    public String reportarTelemetria(@RequestParam(value = "idCamion", defaultValue = "CAM-01") String idCamion,
                                     @RequestParam(value = "patente", defaultValue = "AE789XY") String patente,
                                     @RequestParam("latitud") double latitud,
                                     @RequestParam("longitud") double longitud,
                                     @RequestParam(value = "velocidad", defaultValue = "45.0") double velocidad,
                                     @RequestParam(value = "rutaId", required = false) String rutaId,
                                     RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.reportarTelemetria(idCamion, patente, latitud, longitud, velocidad);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Telemetría GPS actualizada: [" + latitud + ", " + longitud + "] a " + velocidad + " km/h.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la posición de telemetría.");
        }
        String redirectTarget = "/chofer" + ((rutaId != null && !rutaId.isBlank()) ? "?rutaId=" + rutaId : "");
        return "redirect:" + redirectTarget;
    }

    @PostMapping("/entregas/{id}/confirmar")
    public String confirmarEntrega(@PathVariable("id") String id,
                                   @RequestParam(value = "fotoUrl", required = false) String fotoUrl,
                                   @RequestParam(value = "rutaId", required = false) String rutaId,
                                   RedirectAttributes redirectAttrs) {
        String finalFoto = (fotoUrl != null && !fotoUrl.isBlank())
                ? fotoUrl.trim()
                : "https://images.unsplash.com/photo-1593113598332-cd288d649433?auto=format&fit=crop&w=400&q=80";

        boolean ok = apiClient.confirmarEntrega(id, List.of(finalFoto));
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Entrega confirmada exitosamente! Se generó el comprobante y se notificó a las partes.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "Error al confirmar la entrega " + id + ".");
        }
        String redirectTarget = "/chofer" + ((rutaId != null && !rutaId.isBlank()) ? "?rutaId=" + rutaId : "");
        return "redirect:" + redirectTarget;
    }

    @PostMapping("/entregas/{id}/fallida")
    public String entregaFallida(@PathVariable("id") String id,
                                 @RequestParam(value = "motivo", defaultValue = "Comedor cerrado o sin receptor") String motivo,
                                 @RequestParam(value = "replanificable", defaultValue = "true") boolean replanificable,
                                 @RequestParam(value = "rutaId", required = false) String rutaId,
                                 RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.registrarEntregaFallidaLogistica(id, motivo, replanificable);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Entrega registrada como FALLIDA / NO RECIBIDA. Estado actualizado para replanificación o reingreso.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la falla de la entrega " + id + ".");
        }
        String redirectTarget = "/chofer" + ((rutaId != null && !rutaId.isBlank()) ? "?rutaId=" + rutaId : "");
        return "redirect:" + redirectTarget;
    }
}
