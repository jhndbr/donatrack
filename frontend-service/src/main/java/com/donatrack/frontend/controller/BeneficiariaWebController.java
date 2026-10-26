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

@Controller
@RequestMapping("/beneficiaria")
public class BeneficiariaWebController {

    private final ApiClientService apiClient;

    public BeneficiariaWebController(ApiClientService apiClient) {
        this.apiClient = apiClient;
    }

    private Long resolveEntidadId(HttpSession session, Long paramEntidadId) {
        if (session != null) {
            UserSession userSession = (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY);
            if (userSession != null) {
                // Si es administrador, permitirle gestionar y visualizar cualquier entidad
                if (userSession.isAdmin()) {
                    return paramEntidadId != null ? paramEntidadId : 1L;
                }
                if (userSession.getEntidadId() != null) {
                    return userSession.getEntidadId();
                }
            }
        }
        return paramEntidadId != null ? paramEntidadId : 1L;
    }

    @GetMapping
    public String dashboard(Model model,
                            @RequestParam(value = "entidadId", required = false) Long entidadId,
                            HttpSession session) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        List<Map<String, Object>> todas = apiClient.listarDonaciones(null);

        // Donaciones asignadas a esta entidad
        List<Map<String, Object>> donacionesAsignadas = todas.stream()
                .filter(d -> {
                    Object ent = d.get("entidadAsignada");
                    if (ent instanceof Map<?, ?> m) {
                        Object idVal = m.get("id");
                        return idVal != null && resolvedId.equals(((Number) idVal).longValue());
                    }
                    return false;
                })
                .toList();

        String nombreEntidad = entidades.stream()
                .filter(e -> {
                    Object idVal = e.get("id");
                    return idVal != null && resolvedId.equals(((Number) idVal).longValue());
                })
                .map(e -> String.valueOf(e.get("razonSocial")))
                .findFirst().orElse("Mi Entidad Beneficiaria");

        model.addAttribute("entidadId", resolvedId);
        model.addAttribute("nombreEntidad", nombreEntidad);
        model.addAttribute("entidades", entidades);
        model.addAttribute("donacionesAsignadas", donacionesAsignadas);

        return "beneficiaria/dashboard";
    }

    @GetMapping("/notificaciones")
    public String notificaciones(Model model,
                                 @RequestParam(value = "entidadId", required = false) Long entidadId,
                                 HttpSession session) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        String emailEntidad = entidades.stream()
                .filter(e -> {
                    Object idVal = e.get("id");
                    return idVal != null && resolvedId.equals(((Number) idVal).longValue());
                })
                .map(e -> {
                    if (e.get("emailPrincipal") != null && !String.valueOf(e.get("emailPrincipal")).isBlank()) {
                        return String.valueOf(e.get("emailPrincipal"));
                    }
                    if (e.get("emailsRepresentantes") instanceof List<?> list && !list.isEmpty()) {
                        return String.valueOf(list.get(0));
                    }
                    return "";
                })
                .findFirst().orElse("");

        List<Map<String, Object>> notificaciones = (!emailEntidad.isBlank())
                ? apiClient.listarNotificaciones(emailEntidad)
                : Collections.emptyList();

        model.addAttribute("entidadId", resolvedId);
        model.addAttribute("entidades", entidades);
        model.addAttribute("notificaciones", notificaciones != null ? notificaciones : Collections.emptyList());
        model.addAttribute("emailEntidad", emailEntidad);
        return "beneficiaria/notificaciones";
    }

    @GetMapping("/necesidades")
    @SuppressWarnings("unchecked")
    public String necesidadesForm(Model model,
                                  @RequestParam(value = "entidadId", required = false) Long entidadId,
                                  HttpSession session) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        Map<String, Object> entidadActual = entidades.stream()
                .filter(e -> {
                    Object idVal = e.get("id");
                    return idVal != null && resolvedId.equals(((Number) idVal).longValue());
                })
                .findFirst().orElse(Collections.emptyMap());

        String nombreEntidad = entidadActual.get("razonSocial") != null
                ? String.valueOf(entidadActual.get("razonSocial"))
                : "Mi Entidad Beneficiaria";

        List<Map<String, Object>> necesidades = apiClient.listarNecesidadesEntidad(resolvedId);
        if ((necesidades == null || necesidades.isEmpty()) && entidadActual.get("necesidades") instanceof List<?> list) {
            necesidades = (List<Map<String, Object>>) list;
        }

        model.addAttribute("entidadId", resolvedId);
        model.addAttribute("nombreEntidad", nombreEntidad);
        model.addAttribute("entidades", entidades);
        model.addAttribute("necesidades", necesidades != null ? necesidades : Collections.emptyList());
        return "beneficiaria/necesidades";
    }

    @PostMapping("/necesidades")
    public String registrarNecesidad(@RequestParam(value = "entidadId", required = false) Long entidadId,
                                     @RequestParam("subcategoria") String subcategoria,
                                     @RequestParam("descripcion") String descripcion,
                                     @RequestParam("tipo") String tipo,
                                     @RequestParam("cantidadSolicitada") double cantidadSolicitada,
                                     @RequestParam("unidadMedida") String unidadMedida,
                                     @RequestParam(value = "periodo", required = false) String periodo,
                                     HttpSession session,
                                     RedirectAttributes redirectAttrs) {
        Long resolvedId = resolveEntidadId(session, entidadId);

        Map<String, Object> body = Map.of(
                "subcategoria", subcategoria,
                "descripcion", descripcion,
                "tipo", tipo,
                "cantidadSolicitada", cantidadSolicitada,
                "unidadMedida", unidadMedida,
                "periodo", periodo != null ? periodo : ""
        );

        boolean ok = apiClient.crearNecesidad(resolvedId, body);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "¡Necesidad material de " + cantidadSolicitada + " " + unidadMedida + " de " + subcategoria +
                            " (" + tipo + ") registrada correctamente! Ha sido ingresada para el algoritmo de Matchmaking.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la necesidad. Intente nuevamente.");
        }

        return "redirect:/beneficiaria";
    }

    @PostMapping("/necesidades/{id}/editar")
    public String editarNecesidad(@PathVariable("id") Long id,
                                  @RequestParam(value = "entidadId", required = false) Long entidadId,
                                  @RequestParam(value = "subcategoria", required = false) String subcategoria,
                                  @RequestParam(value = "descripcion", required = false) String descripcion,
                                  @RequestParam(value = "tipo", defaultValue = "EXTRAORDINARIA") String tipo,
                                  @RequestParam(value = "cantidadSolicitada", defaultValue = "10.0") double cantidadSolicitada,
                                  @RequestParam(value = "unidadMedida", defaultValue = "kg") String unidadMedida,
                                  @RequestParam(value = "periodo", required = false) String periodo,
                                  HttpSession session,
                                  RedirectAttributes redirectAttrs) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        Map<String, Object> body = new java.util.HashMap<>();
        if (subcategoria != null && !subcategoria.isBlank()) body.put("subcategoria", subcategoria);
        if (descripcion != null && !descripcion.isBlank()) body.put("descripcion", descripcion);
        body.put("tipo", tipo);
        body.put("cantidadSolicitada", cantidadSolicitada);
        body.put("unidadMedida", unidadMedida);
        body.put("periodo", periodo != null ? periodo : "");

        boolean ok = apiClient.actualizarNecesidad(id, body) || apiClient.actualizarNecesidad(resolvedId, id, body);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Necesidad #" + id + " actualizada correctamente.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo actualizar la necesidad #" + id + ".");
        }
        return "redirect:/beneficiaria/necesidades";
    }

    @PostMapping("/necesidades/{id}/eliminar")
    public String eliminarNecesidad(@PathVariable("id") Long id,
                                    @RequestParam(value = "entidadId", required = false) Long entidadId,
                                    HttpSession session,
                                    RedirectAttributes redirectAttrs) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        boolean ok = apiClient.eliminarNecesidad(id) || apiClient.eliminarNecesidad(resolvedId, id);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Necesidad #" + id + " eliminada / cerrada correctamente.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo eliminar la necesidad #" + id + ".");
        }
        return "redirect:/beneficiaria/necesidades";
    }

    @GetMapping("/confirmar")
    public String confirmarForm(Model model,
                                @RequestParam(value = "donacionId", required = false) Long donacionId,
                                @RequestParam(value = "entidadId", required = false) Long entidadId,
                                HttpSession session) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        List<Map<String, Object>> todas = apiClient.listarDonaciones(null);

        // Donaciones asignadas exclusivamente a esta entidad que aún no están entregadas
        List<Map<String, Object>> donacionesConfirmables = todas.stream()
                .filter(d -> {
                    if ("ENTREGADA".equals(d.get("estado"))) return false;
                    Object ent = d.get("entidadAsignada");
                    if (ent instanceof Map<?, ?> m) {
                        Object idVal = m.get("id");
                        return idVal != null && resolvedId.equals(((Number) idVal).longValue());
                    }
                    return false;
                })
                .toList();

        List<Map<String, Object>> rutas = apiClient.listarRutas();

        model.addAttribute("entidadId", resolvedId);
        model.addAttribute("entidades", entidades);
        model.addAttribute("donacionesConfirmables", donacionesConfirmables);
        model.addAttribute("donacionIdSeleccionada", donacionId);
        model.addAttribute("rutas", rutas);

        return "beneficiaria/confirmar";
    }

    @PostMapping("/confirmar")
    public String confirmarRecepcion(@RequestParam(value = "donacionId", required = false) Long donacionId,
                                     @RequestParam(value = "entidadId", required = false) Long entidadId,
                                     @RequestParam(value = "idEntrega", required = false) String idEntrega,
                                     @RequestParam(value = "fotoUrl", required = false) String fotoUrl,
                                     @RequestParam(value = "fotoFile", required = false) org.springframework.web.multipart.MultipartFile fotoFile,
                                     @RequestParam(value = "observaciones", required = false) String observaciones,
                                     HttpSession session,
                                     RedirectAttributes redirectAttrs) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        UserSession userSession = session != null ? (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY) : null;

        if (donacionId != null) {
            Map<String, Object> donacion = apiClient.obtenerDonacion(donacionId);
            if (donacion != null && !donacion.isEmpty() && donacion.get("entidadAsignada") instanceof Map<?, ?> entMap) {
                Object idVal = entMap.get("id");
                if (idVal instanceof Number entNum && (userSession == null || !userSession.isAdmin())) {
                    if (!resolvedId.equals(entNum.longValue())) {
                        redirectAttrs.addFlashAttribute("mensajeError", "No tiene permisos para operar sobre una donación asignada a otra entidad.");
                        return "redirect:/beneficiaria" + (entidadId != null ? "?entidadId=" + entidadId : "");
                    }
                }
            }
        }

        String urlImagenFinal = fotoUrl;
        if (fotoFile != null && !fotoFile.isEmpty()) {
            String urlSubida = apiClient.subirImagenDonacion(fotoFile);
            if (urlSubida != null) {
                urlImagenFinal = urlSubida;
            }
        }

        if (urlImagenFinal == null || urlImagenFinal.isBlank()) {
            urlImagenFinal = "https://images.unsplash.com/photo-1593113598332-cd288d649433?auto=format&fit=crop&w=600&q=80";
        }

        boolean ok = false;
        if (donacionId != null) {
            if (idEntrega == null || idEntrega.isBlank()) {
                ok = apiClient.confirmarEntregaPorDonacion(donacionId, urlImagenFinal, observaciones);
            }
            if (!ok) {
                ok = apiClient.entregarDonacion(donacionId, urlImagenFinal);
            }
        }

        if (idEntrega != null && !idEntrega.isBlank()) {
            boolean okLog = apiClient.confirmarEntrega(idEntrega, List.of(urlImagenFinal));
            ok = ok || okLog;
        }

        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "¡Recepción confirmada con éxito! La donación ha sido registrada como ENTREGADA. " +
                            "Se emitió el comprobante de trazabilidad y la fotografía ya es visible públicamente en DonaTrack.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo confirmar la recepción. Verifique los datos ingresados.");
        }

        return "redirect:/beneficiaria" + (entidadId != null ? "?entidadId=" + entidadId : "");
    }

    @GetMapping("/tracking")
    public String tracking(Model model,
                           @RequestParam(value = "entidadId", required = false) Long entidadId,
                           HttpSession session) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        List<Map<String, Object>> camiones = apiClient.listarCamiones();
        List<Map<String, Object>> flotaEnVivo = apiClient.obtenerFlotaEnVivo();
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        List<Map<String, Object>> todas = apiClient.listarDonaciones(null);

        Map<String, Object> entidadActual = entidades.stream()
                .filter(e -> {
                    Object idVal = e.get("id");
                    return idVal != null && resolvedId.equals(((Number) idVal).longValue());
                })
                .findFirst().orElse(Collections.emptyMap());

        List<Map<String, Object>> misDonaciones = todas.stream()
                .filter(d -> {
                    Object ent = d.get("entidadAsignada");
                    if (ent instanceof Map<?, ?> m) {
                        Object idVal = m.get("id");
                        return idVal != null && resolvedId.equals(((Number) idVal).longValue());
                    }
                    return false;
                })
                .toList();

        model.addAttribute("entidadId", resolvedId);
        model.addAttribute("entidadActual", entidadActual);
        model.addAttribute("misDonaciones", misDonaciones);
        model.addAttribute("camiones", camiones);
        model.addAttribute("flotaEnVivo", flotaEnVivo);
        return "beneficiaria/tracking";
    }

    @PostMapping("/fallida")
    public String registrarEntregaFallida(@RequestParam(value = "donacionId", required = false) Long donacionId,
                                          @RequestParam(value = "entidadId", required = false) Long entidadId,
                                          @RequestParam(value = "idEntrega", required = false) String idEntrega,
                                          @RequestParam("motivo") String motivo,
                                          @RequestParam(value = "replanificable", defaultValue = "true") boolean replanificable,
                                          HttpSession session,
                                          RedirectAttributes redirectAttrs) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        UserSession userSession = session != null ? (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY) : null;

        if (donacionId != null) {
            Map<String, Object> donacion = apiClient.obtenerDonacion(donacionId);
            if (donacion != null && !donacion.isEmpty() && donacion.get("entidadAsignada") instanceof Map<?, ?> entMap) {
                Object idVal = entMap.get("id");
                if (idVal instanceof Number entNum && (userSession == null || !userSession.isAdmin())) {
                    if (!resolvedId.equals(entNum.longValue())) {
                        redirectAttrs.addFlashAttribute("mensajeError", "No tiene permisos para operar sobre una donación asignada a otra entidad.");
                        return "redirect:/beneficiaria";
                    }
                }
            }
        }

        boolean ok = false;
        String detalle = "";

        if (donacionId != null) {
            boolean okDon = false;
            if (idEntrega == null || idEntrega.isBlank()) {
                okDon = apiClient.registrarEntregaFallidaPorDonacion(donacionId, motivo, replanificable);
            }
            if (!okDon) {
                okDon = apiClient.actualizarEstadoDonacion(donacionId, "ENTREGA_FALLIDA", "Rechazo de entidad: " + motivo);
            }
            if (okDon) {
                ok = true;
                detalle = "Donación DON-" + String.format("%04d", donacionId);
            }
        }

        if (idEntrega != null && !idEntrega.isBlank()) {
            boolean okLog = apiClient.registrarEntregaFallida(idEntrega, motivo, replanificable);
            if (okLog) {
                ok = true;
                detalle = (detalle.isEmpty() ? "" : detalle + " / ") + "Lote " + idEntrega;
            }
        }

        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "Incidencia registrada formalmente (" + detalle + "): Reportada como ENTREGA FALLIDA / RECHAZADA. " +
                            "El bien queda a disposición del Administrador para su reingreso físico a depósito (CU-45).");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la entrega fallida. Verifique que seleccionó una donación válida.");
        }
        return "redirect:/beneficiaria";
    }

    @GetMapping("/comprobante/{idEntrega}")
    @ResponseBody
    public Map<String, Object> obtenerComprobante(@PathVariable("idEntrega") String idEntrega) {
        return apiClient.obtenerComprobanteEntrega(idEntrega);
    }
}
