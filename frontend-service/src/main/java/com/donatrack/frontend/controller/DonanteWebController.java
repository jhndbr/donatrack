package com.donatrack.frontend.controller;

import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/donante")
public class DonanteWebController {

    private final ApiClientService apiClient;

    public DonanteWebController(ApiClientService apiClient) {
        this.apiClient = apiClient;
    }

    private Long resolveDonanteId(HttpSession session, Long paramDonanteId) {
        if (session != null) {
            UserSession userSession = (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY);
            if (userSession != null && userSession.getDonanteId() != null) {
                return userSession.getDonanteId();
            }
        }
        return paramDonanteId != null ? paramDonanteId : 1L;
    }

    @GetMapping
    public String dashboard(Model model,
                            @RequestParam(value = "donanteId", required = false) Long donanteId,
                            HttpSession session) {
        Long resolvedId = resolveDonanteId(session, donanteId);
        List<Map<String, Object>> donaciones = apiClient.listarDonaciones(null);
        Map<String, Object> metricas = apiClient.obtenerMetricasDonante(resolvedId);
        List<Map<String, Object>> insignias = apiClient.listarInsigniasDonante(resolvedId);

        model.addAttribute("donanteId", resolvedId);
        model.addAttribute("donaciones", donaciones);
        model.addAttribute("metricas", metricas);
        model.addAttribute("insignias", insignias);

        return "donante/dashboard";
    }

    @GetMapping("/nueva-donacion")
    public String nuevaDonacionForm(Model model,
                                   @RequestParam(value = "subcategoria", required = false) String subcategoria,
                                   @RequestParam(value = "entidadId", required = false) Long entidadId,
                                   HttpSession session) {
        Long resolvedId = resolveDonanteId(session, null);
        List<Map<String, Object>> entidades = apiClient.listarEntidades();

        model.addAttribute("donanteId", resolvedId);
        model.addAttribute("entidades", entidades);
        model.addAttribute("subcategoriaPreseleccionada", subcategoria != null ? subcategoria : "");
        model.addAttribute("entidadIdPreseleccionada", entidadId);

        return "donante/nueva_donacion";
    }

    @PostMapping("/donar")
    public String registrarDonacion(@RequestParam(value = "donanteId", required = false) Long donanteId,
                                    @RequestParam("categoria") String categoria,
                                    @RequestParam("subcategoria") String subcategoria,
                                    @RequestParam("cantidad") double cantidad,
                                    @RequestParam("unidadMedida") String unidadMedida,
                                    @RequestParam(value = "condicion", defaultValue = "NUEVO") String condicion,
                                    @RequestParam(value = "fechaVencimiento", required = false) String fechaVencimiento,
                                    @RequestParam(value = "descripcion", required = false) String descripcion,
                                    @RequestParam(value = "fotoUrl", required = false) String fotoUrl,
                                    HttpSession session,
                                    RedirectAttributes redirectAttrs) {
        Long resolvedId = resolveDonanteId(session, donanteId);

        Map<String, Object> bien = new HashMap<>();
        bien.put("subcategoria", subcategoria);
        bien.put("categoria", categoria);
        bien.put("cantidad", cantidad);
        bien.put("unidadMedida", unidadMedida);
        bien.put("condicion", condicion);
        bien.put("descripcion", (descripcion != null && !descripcion.isBlank()) ? descripcion : (subcategoria + " (" + cantidad + " " + unidadMedida + ")"));
        if (fechaVencimiento != null && !fechaVencimiento.isBlank()) {
            bien.put("fechaVencimiento", fechaVencimiento);
        }
        if (fotoUrl != null && !fotoUrl.isBlank()) {
            bien.put("fotoUrl", fotoUrl);
        }

        boolean ok = apiClient.crearDonacionSegmentada(resolvedId, List.of(bien));
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "¡Tu donación de " + cantidad + " " + unidadMedida + " de " + subcategoria +
                            " fue ingresada con éxito al depósito! Iniciando segmentación y evaluación de matchmaking.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la donación. Intente nuevamente.");
        }

        return "redirect:/donante/donaciones";
    }

    @GetMapping("/donaciones")
    public String donaciones(Model model,
                             @RequestParam(value = "estado", required = false) String estado,
                             @RequestParam(value = "subcategoria", required = false) String subcategoria) {
        List<Map<String, Object>> todas = apiClient.listarDonaciones(estado);
        List<Map<String, Object>> filtradas = todas;

        if (subcategoria != null && !subcategoria.isBlank()) {
            filtradas = todas.stream()
                    .filter(d -> subcategoria.equalsIgnoreCase((String) d.get("subcategoria")))
                    .toList();
        }

        model.addAttribute("donaciones", filtradas);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("subcategoriaSeleccionada", subcategoria);

        return "donante/donaciones";
    }

    @GetMapping("/incentivos")
    public String incentivos(Model model,
                             @RequestParam(value = "donanteId", required = false) Long donanteId,
                             HttpSession session) {
        Long resolvedId = resolveDonanteId(session, donanteId);
        Map<String, Object> metricas = apiClient.obtenerMetricasDonante(resolvedId);
        List<Map<String, Object>> misionesProgreso = apiClient.listarMisionesDonante(resolvedId);
        List<Map<String, Object>> insignias = apiClient.listarInsigniasDonante(resolvedId);
        List<Map<String, Object>> ranking = apiClient.obtenerRankingMensual(null, null);

        model.addAttribute("donanteId", resolvedId);
        model.addAttribute("metricas", metricas);
        model.addAttribute("misiones", misionesProgreso);
        model.addAttribute("insignias", insignias);
        model.addAttribute("ranking", ranking);

        return "donante/incentivos";
    }

    @GetMapping("/tracking")
    public String tracking(Model model) {
        List<Map<String, Object>> camiones = apiClient.listarCamiones();
        List<Map<String, Object>> rutas = apiClient.listarRutas();

        model.addAttribute("camiones", camiones);
        model.addAttribute("rutas", rutas);

        return "donante/tracking";
    }

    @GetMapping("/entidades")
    public String entidades(Model model) {
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        model.addAttribute("entidades", entidades);
        return "donante/entidades";
    }
}
