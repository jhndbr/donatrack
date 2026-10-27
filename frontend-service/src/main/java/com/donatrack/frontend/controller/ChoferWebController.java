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
        List<Map<String, Object>> paradasRaw = (rutaActiva != null && rutaActiva.containsKey("paradas") && rutaActiva.get("paradas") instanceof List<?> pList)
                ? (List<Map<String, Object>>) pList
                : Collections.emptyList();

        // Sincronizar en memoria cada parada con su documento Entrega para reflejar confirmaciones del receptor en tiempo real
        List<Map<String, Object>> paradas = new java.util.ArrayList<>();
        for (Map<String, Object> p : paradasRaw) {
            Map<String, Object> paradaEnriquecida = new java.util.HashMap<>(p);
            Object idDonObj = p.get("idDonacion");
            if (idDonObj instanceof Number donNum && entregas != null) {
                long idDon = donNum.longValue();
                for (Map<String, Object> ent : entregas) {
                    if (ent.get("idDonacion") instanceof Number entDon && entDon.longValue() == idDon) {
                        String estEnt = ent.get("estado") != null ? String.valueOf(ent.get("estado")) : "PENDIENTE";
                        String etapaOp = ent.get("etapaOperativa") != null ? String.valueOf(ent.get("etapaOperativa")) : "PENDIENTE_RETIRO_DEPOSITO";
                        paradaEnriquecida.put("idEntrega", ent.get("id"));
                        paradaEnriquecida.put("estadoEntrega", estEnt);
                        paradaEnriquecida.put("etapaOperativa", etapaOp);
                        paradaEnriquecida.put("receptorNombre", ent.get("receptorNombre"));
                        paradaEnriquecida.put("receptorDni", ent.get("receptorDni"));
                        if ("ENTREGADA".equals(estEnt)) {
                            paradaEnriquecida.put("completada", true);
                            paradaEnriquecida.put("estadoParada", "ENTREGADA");
                        } else if ("NO_RECIBIDA".equals(estEnt)) {
                            paradaEnriquecida.put("completada", false);
                            paradaEnriquecida.put("estadoParada", "FALLIDA");
                            if (ent.get("motivoFalla") != null) {
                                paradaEnriquecida.put("motivoFalla", ent.get("motivoFalla"));
                            }
                        } else if ("EN_PUERTA_SEDE".equals(etapaOp)) {
                            paradaEnriquecida.put("estadoParada", "EN_PUERTA_SEDE");
                        }
                        if (paradaEnriquecida.get("nombreEntidad") == null
                                || String.valueOf(paradaEnriquecida.get("nombreEntidad")).startsWith("Entidad Beneficiaria Destino")) {
                            if (ent.get("nombreEntidad") != null) {
                                paradaEnriquecida.put("nombreEntidad", ent.get("nombreEntidad"));
                            }
                        }
                        break;
                    }
                }
            }
            if (paradaEnriquecida.get("estadoParada") == null) {
                paradaEnriquecida.put("estadoParada", Boolean.TRUE.equals(paradaEnriquecida.get("completada")) ? "ENTREGADA" : "PENDIENTE");
            }
            paradas.add(paradaEnriquecida);
        }

        List<Map<String, Object>> flotaEnVivo = apiClient.obtenerFlotaEnVivo();

        model.addAttribute("rutas", rutas);
        model.addAttribute("camiones", camiones);
        model.addAttribute("flotaEnVivo", flotaEnVivo);
        model.addAttribute("rutaActiva", rutaActiva);
        model.addAttribute("paradas", paradas);
        model.addAttribute("entregas", entregas);
        model.addAttribute("userSession", userSession);

        return "chofer/ruta";
    }

    @PostMapping("/rutas/planificar-ahora")
    public String planificarRutasAhora(RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.planificarRutasPendientesAhora();
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "¡Planificación de rutas ejecutada! Se generaron las hojas de ruta optimizadas para las donaciones en estado ASIGNACION_REALIZADA.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError",
                    "No se pudo disparar la planificación de rutas en este momento.");
        }
        return "redirect:/chofer";
    }

    @PostMapping("/rutas/{id}/iniciar")
    public String iniciarRuta(@PathVariable("id") String id,
                              RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.iniciarRuta(id);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "¡Retiro en Depósito confirmado y Ruta iniciada! Las donaciones fueron cargadas en el camión (EN_TRASLADO) y se notificó a donantes y entidades con el link de seguimiento GPS.");
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

    /**
     * Endpoint AJAX para transmisión continua de geolocalización desde el navegador (navigator.geolocation Web API).
     */
    @PostMapping("/telemetria/ajax")
    @ResponseBody
    public Map<String, Object> reportarTelemetriaAjax(@RequestParam(value = "idCamion", defaultValue = "CAM-01") String idCamion,
                                                      @RequestParam(value = "patente", defaultValue = "AE789XY") String patente,
                                                      @RequestParam("latitud") double latitud,
                                                      @RequestParam("longitud") double longitud,
                                                      @RequestParam(value = "velocidad", defaultValue = "40.0") double velocidad) {
        boolean ok = apiClient.reportarTelemetria(idCamion, patente, latitud, longitud, velocidad);
        return Map.of(
                "exito", ok,
                "patente", patente,
                "latitud", latitud,
                "longitud", longitud,
                "velocidad", velocidad,
                "timestamp", java.time.LocalDateTime.now().toString()
        );
    }

    /**
     * Registra que el chofer ha llegado a la puerta del comedor / entidad beneficiaria (EN_PUERTA_SEDE)
     * para iniciar el acto de entrega física con el receptor de la entidad.
     */
    @PostMapping("/entregas/{idDonacion}/arribo")
    public String registrarArriboSede(@PathVariable("idDonacion") Long idDonacion,
                                      @RequestParam(value = "observaciones", required = false) String observaciones,
                                      @RequestParam(value = "rutaId", required = false) String rutaId,
                                      RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.registrarArriboSedePorDonacion(idDonacion, observaciones);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "🚚 ¡Arribo en puerta de sede registrado para Donación #" + idDonacion + "! " +
                            "La posición GPS del camión fue actualizada en el destino y la Entidad Beneficiaria ya visualiza el aviso para firmar o confirmar la recepción.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar el arribo en puerta para la donación #" + idDonacion + ".");
        }
        String redirectTarget = "/chofer" + ((rutaId != null && !rutaId.isBlank()) ? "?rutaId=" + rutaId : "");
        return "redirect:" + redirectTarget;
    }

    @PostMapping("/entregas/{id}/confirmar")
    public String confirmarEntrega(@PathVariable("id") String id,
                                   @RequestParam(value = "fotoUrl", required = false) String fotoUrl,
                                   @RequestParam(value = "fotoFile", required = false) org.springframework.web.multipart.MultipartFile fotoFile,
                                   @RequestParam(value = "receptorNombre", required = false) String receptorNombre,
                                   @RequestParam(value = "receptorDni", required = false) String receptorDni,
                                   @RequestParam(value = "observaciones", required = false) String observaciones,
                                   @RequestParam(value = "rutaId", required = false) String rutaId,
                                   RedirectAttributes redirectAttrs) {
        String finalFoto = fotoUrl;
        if (fotoFile != null && !fotoFile.isEmpty()) {
            String subida = apiClient.subirImagenDonacion(fotoFile);
            if (subida != null) {
                finalFoto = subida;
            }
        }
        if (finalFoto == null || finalFoto.isBlank()) {
            finalFoto = "https://images.unsplash.com/photo-1593113598332-cd288d649433?auto=format&fit=crop&w=400&q=80";
        } else {
            finalFoto = finalFoto.trim();
        }

        boolean ok = false;
        if (id != null && id.matches("\\d+")) {
            Long idDonacion = Long.parseLong(id);
            if ((receptorNombre != null && !receptorNombre.isBlank()) || (receptorDni != null && !receptorDni.isBlank())) {
                ok = apiClient.confirmarEntregaPorDonacion(idDonacion, finalFoto, observaciones, receptorNombre, receptorDni);
            }
            if (!ok) {
                ok = apiClient.confirmarEntregaPorDonacion(idDonacion, finalFoto, observaciones);
            }
            if (!ok) {
                ok = apiClient.confirmarEntrega(id, List.of(finalFoto));
            }
            if (!ok) {
                ok = apiClient.entregarDonacion(idDonacion, finalFoto);
            }
        } else if (id != null && !id.isBlank()) {
            ok = apiClient.confirmarEntrega(id, List.of(finalFoto));
        }

        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "¡Acta de Entrega confirmada exitosamente" +
                            (receptorNombre != null && !receptorNombre.isBlank() ? " (Receptor: " + receptorNombre + ")" : "") +
                            "! Se sincronizó el estado ENTREGADA con la Entidad Beneficiaria y el Donante.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "Error al confirmar la entrega " + id + ".");
        }
        String redirectTarget = "/chofer" + ((rutaId != null && !rutaId.isBlank()) ? "?rutaId=" + rutaId : "");
        return "redirect:" + redirectTarget;
    }

    @PostMapping("/entregas/{id}/fallida")
    public String entregaFallida(@PathVariable("id") String id,
                                 @RequestParam(value = "etapaFalla", required = false) String etapaFalla,
                                 @RequestParam(value = "motivo", defaultValue = "Comedor cerrado o sin receptor") String motivo,
                                 @RequestParam(value = "replanificable", defaultValue = "true") boolean replanificable,
                                 @RequestParam(value = "rutaId", required = false) String rutaId,
                                 RedirectAttributes redirectAttrs) {
        String motivoCompleto = (etapaFalla != null && !etapaFalla.isBlank())
                ? "[" + etapaFalla + "] " + motivo
                : motivo;

        boolean ok = false;
        if (id != null && id.matches("\\d+")) {
            Long idDonacion = Long.parseLong(id);
            ok = apiClient.registrarEntregaFallidaPorDonacion(idDonacion, motivoCompleto, replanificable);
            if (!ok) {
                ok = apiClient.registrarEntregaFallidaLogistica(id, motivoCompleto, replanificable);
            }
            if (!ok) {
                ok = apiClient.actualizarEstadoDonacion(idDonacion, "ENTREGA_FALLIDA", motivoCompleto);
            }
        } else if (id != null && !id.isBlank()) {
            ok = apiClient.registrarEntregaFallidaLogistica(id, motivoCompleto, replanificable);
        }

        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "Incidencia registrada como FALLIDA / NO RECIBIDA (" + motivoCompleto + "). " +
                            "Se notificó a Donante, Entidad y Administración para gestión de reingreso o replanificación (CU-44 / CU-45).");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la falla de la entrega " + id + ".");
        }
        String redirectTarget = "/chofer" + ((rutaId != null && !rutaId.isBlank()) ? "?rutaId=" + rutaId : "");
        return "redirect:" + redirectTarget;
    }

    @GetMapping("/comprobante/donacion/{idDonacion}")
    @ResponseBody
    public Map<String, Object> obtenerComprobantePorDonacion(@PathVariable("idDonacion") Long idDonacion) {
        return apiClient.obtenerComprobanteEntregaPorDonacion(idDonacion);
    }
}
