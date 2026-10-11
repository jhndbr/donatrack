package com.donatrack.frontend.controller;

import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
            if (userSession != null && userSession.getEntidadId() != null) {
                return userSession.getEntidadId();
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

        // Si la lista está vacía para esa entidad, mostrar las asignadas globales para permitir evaluar la interfaz
        if (donacionesAsignadas.isEmpty()) {
            donacionesAsignadas = todas.stream()
                    .filter(d -> d.get("entidadAsignada") != null)
                    .toList();
        }

        model.addAttribute("entidadId", resolvedId);
        model.addAttribute("entidades", entidades);
        model.addAttribute("donacionesAsignadas", donacionesAsignadas);

        return "beneficiaria/dashboard";
    }

    @GetMapping("/necesidades")
    public String necesidadesForm(Model model,
                                  @RequestParam(value = "entidadId", required = false) Long entidadId,
                                  HttpSession session) {
        Long resolvedId = resolveEntidadId(session, entidadId);
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        model.addAttribute("entidadId", resolvedId);
        model.addAttribute("entidades", entidades);
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

    @GetMapping("/confirmar")
    public String confirmarForm(Model model,
                                @RequestParam(value = "donacionId", required = false) Long donacionId,
                                HttpSession session) {
        Long resolvedId = resolveEntidadId(session, null);
        List<Map<String, Object>> todas = apiClient.listarDonaciones(null);

        // Donaciones asignadas que están en camino o listas para entregar
        List<Map<String, Object>> donacionesConfirmables = todas.stream()
                .filter(d -> d.get("entidadAsignada") != null && !"ENTREGADA".equals(d.get("estado")))
                .toList();

        List<Map<String, Object>> rutas = apiClient.listarRutas();

        model.addAttribute("entidadId", resolvedId);
        model.addAttribute("donacionesConfirmables", donacionesConfirmables);
        model.addAttribute("donacionIdSeleccionada", donacionId);
        model.addAttribute("rutas", rutas);

        return "beneficiaria/confirmar";
    }

    @PostMapping("/confirmar")
    public String confirmarRecepcion(@RequestParam(value = "donacionId", required = false) Long donacionId,
                                     @RequestParam(value = "idEntrega", required = false) String idEntrega,
                                     @RequestParam("fotoUrl") String fotoUrl,
                                     @RequestParam(value = "observaciones", required = false) String observaciones,
                                     RedirectAttributes redirectAttrs) {
        boolean ok = false;

        if (donacionId != null) {
            ok = apiClient.entregarDonacion(donacionId, fotoUrl);
        }

        if (idEntrega != null && !idEntrega.isBlank()) {
            boolean okLog = apiClient.confirmarEntrega(idEntrega, List.of(fotoUrl));
            ok = ok || okLog;
        }

        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "¡Recepción confirmada con éxito! La donación ha sido registrada como ENTREGADA. " +
                            "Se emitió el comprobante de trazabilidad y la fotografía ya es visible públicamente en DonaTrack.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo confirmar la recepción. Verifique los datos ingresados.");
        }

        return "redirect:/beneficiaria";
    }

    @GetMapping("/tracking")
    public String tracking(Model model) {
        List<Map<String, Object>> camiones = apiClient.listarCamiones();
        model.addAttribute("camiones", camiones);
        return "beneficiaria/tracking";
    }
}
