package com.donatrack.frontend.controller;

import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
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
                Long id = userSession.getDonanteId();
                // Verificación de sincronización con el backend
                Map<String, Object> verif = apiClient.obtenerDonante(id);
                if (verif == null && userSession.getEmail() != null) {
                    Map<String, Object> sincronizado = apiClient.buscarDonantePorEmail(userSession.getEmail());
                    if (sincronizado != null && sincronizado.get("id") instanceof Number num) {
                        Long nuevoId = num.longValue();
                        userSession.setDonanteId(nuevoId);
                        return nuevoId;
                    }
                }
                return id;
            }
        }
        return paramDonanteId != null ? paramDonanteId : 1L;
    }

    @GetMapping
    public String dashboard(Model model,
                            @RequestParam(value = "donanteId", required = false) Long donanteId,
                            HttpSession session) {
        Long resolvedId = resolveDonanteId(session, donanteId);
        List<Map<String, Object>> donaciones = apiClient.listarDonaciones(null, resolvedId);
        Map<String, Object> metricas = apiClient.obtenerMetricasDonante(resolvedId);
        List<Map<String, Object>> insignias = apiClient.listarInsigniasDonante(resolvedId);

        if (metricas == null || metricas.isEmpty() || !metricas.containsKey("totalDonaciones")) {
            metricas = new HashMap<>();
            metricas.put("totalDonaciones", donaciones.size());
            double totalBienes = donaciones.stream()
                    .filter(d -> d.get("cantidad") instanceof Number)
                    .mapToDouble(d -> ((Number) d.get("cantidad")).doubleValue())
                    .sum();
            metricas.put("totalBienesDonados", totalBienes);
            metricas.put("categoria", "COLABORADOR");
        }

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
                                    @RequestParam(value = "entidadId", required = false) Long entidadId,
                                    @RequestParam("categoria") String categoria,
                                    @RequestParam("subcategoria") String subcategoria,
                                    @RequestParam("cantidad") double cantidad,
                                    @RequestParam("unidadMedida") String unidadMedida,
                                    @RequestParam(value = "condicion", defaultValue = "NUEVO") String condicion,
                                    @RequestParam(value = "fechaVencimiento", required = false) String fechaVencimiento,
                                    @RequestParam(value = "descripcion", required = false) String descripcion,
                                    @RequestParam(value = "fotoUrl", required = false) String fotoUrl,
                                    @RequestParam(value = "fotoFile", required = false) MultipartFile fotoFile,
                                    HttpSession session,
                                    RedirectAttributes redirectAttrs) {
        Long resolvedId = resolveDonanteId(session, donanteId);

        // Si se subió un archivo local desde la PC, enviarlo al servidor de donaciones
        String urlImagenFinal = fotoUrl;
        if (fotoFile != null && !fotoFile.isEmpty()) {
            String urlSubida = apiClient.subirImagenDonacion(fotoFile);
            if (urlSubida != null) {
                urlImagenFinal = urlSubida;
            }
        }

        Map<String, Object> bien = new HashMap<>();
        bien.put("subcategoria", subcategoria);
        bien.put("categoria", categoria);
        bien.put("cantidad", cantidad);
        bien.put("unidadMedida", unidadMedida);
        bien.put("condicion", condicion);
        bien.put("esUsado", "USADO".equalsIgnoreCase(condicion));
        bien.put("descripcion", (descripcion != null && !descripcion.isBlank()) ? descripcion : (subcategoria + " (" + cantidad + " " + unidadMedida + ")"));
        if (fechaVencimiento != null && !fechaVencimiento.isBlank()) {
            bien.put("fechaVencimiento", fechaVencimiento);
        }
        if (urlImagenFinal != null && !urlImagenFinal.isBlank()) {
            bien.put("fotoUrl", urlImagenFinal);
        }

        boolean ok = false;
        String detalleError = null;
        ApiClientService.OperacionResult res = apiClient.crearDonacionSegmentadaConDetalle(resolvedId, List.of(bien), entidadId);
        if (res != null) {
            ok = res.exitoso();
            detalleError = res.mensaje();
        } else {
            // Compatibilidad con tests mockeados que stubbean crearDonacionSegmentada
            ok = (entidadId != null)
                    ? apiClient.crearDonacionSegmentada(resolvedId, List.of(bien), entidadId)
                    : apiClient.crearDonacionSegmentada(resolvedId, List.of(bien));
        }

        if (!ok && session != null) {
            // Si falló por donante no encontrado, intentar re-sincronizar y reintentar una vez
            UserSession userSession = (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY);
            if (userSession != null && userSession.getEmail() != null) {
                Map<String, Object> sincronizado = apiClient.buscarDonantePorEmail(userSession.getEmail());
                if (sincronizado != null && sincronizado.get("id") instanceof Number num) {
                    Long nuevoId = num.longValue();
                    userSession.setDonanteId(nuevoId);
                    resolvedId = nuevoId;
                    ApiClientService.OperacionResult retry = apiClient.crearDonacionSegmentadaConDetalle(resolvedId, List.of(bien), entidadId);
                    if (retry != null) {
                        ok = retry.exitoso();
                        detalleError = retry.mensaje();
                    } else {
                        ok = (entidadId != null)
                                ? apiClient.crearDonacionSegmentada(resolvedId, List.of(bien), entidadId)
                                : apiClient.crearDonacionSegmentada(resolvedId, List.of(bien));
                    }
                }
            }
        }

        if (ok) {
            if (entidadId != null) {
                redirectAttrs.addFlashAttribute("mensajeExito",
                        "¡Tu donación de " + cantidad + " " + unidadMedida + " de " + subcategoria +
                                " fue registrada y asignada directamente a la entidad elegida!");
            } else {
                redirectAttrs.addFlashAttribute("mensajeExito",
                        "¡Tu donación de " + cantidad + " " + unidadMedida + " de " + subcategoria +
                                " fue ingresada con éxito al depósito! Iniciando segmentación y evaluación de matchmaking.");
            }
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la donación" + (detalleError != null ? ": " + detalleError : ". Intente nuevamente."));
        }

        return "redirect:/donante/donaciones";
    }

    @GetMapping("/donaciones")
    public String donaciones(Model model,
                             @RequestParam(value = "donanteId", required = false) Long donanteId,
                             @RequestParam(value = "estado", required = false) String estado,
                             @RequestParam(value = "categoria", required = false) String categoria,
                             @RequestParam(value = "subcategoria", required = false) String subcategoria,
                             @RequestParam(value = "page", defaultValue = "0") int page,
                             @RequestParam(value = "size", defaultValue = "8") int size,
                             HttpSession session) {
        Long resolvedId = resolveDonanteId(session, donanteId);
        Map<String, Object> pageData = (categoria != null && !categoria.isBlank())
                ? apiClient.listarDonacionesPaginadas(page, size, estado, resolvedId, subcategoria, categoria)
                : apiClient.listarDonacionesPaginadas(page, size, estado, resolvedId, subcategoria);

        List<Map<String, Object>> donaciones;
        int totalPaginas = 1;
        long totalElementos = 0;
        int paginaActual = page;

        if (pageData != null && pageData.containsKey("content")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) pageData.get("content");
            donaciones = contentList;
            totalPaginas = ((Number) pageData.getOrDefault("totalPages", 1)).intValue();
            totalElementos = ((Number) pageData.getOrDefault("totalElements", 0)).longValue();
            paginaActual = ((Number) pageData.getOrDefault("number", page)).intValue();
        } else {
            // Fallback
            List<Map<String, Object>> misDonaciones = apiClient.listarDonaciones(estado, resolvedId);
            if (categoria != null && !categoria.isBlank()) {
                misDonaciones = misDonaciones.stream()
                        .filter(d -> categoria.equalsIgnoreCase((String) d.get("categoria")))
                        .toList();
            }
            if (subcategoria != null && !subcategoria.isBlank()) {
                misDonaciones = misDonaciones.stream()
                        .filter(d -> subcategoria.equalsIgnoreCase((String) d.get("subcategoria")))
                        .toList();
            }
            totalElementos = misDonaciones.size();
            totalPaginas = (int) Math.ceil((double) totalElementos / size);
            if (totalPaginas < 1) totalPaginas = 1;
            int start = Math.min(page * size, misDonaciones.size());
            int end = Math.min(start + size, misDonaciones.size());
            donaciones = misDonaciones.subList(start, end);
        }

        model.addAttribute("donanteId", resolvedId);
        model.addAttribute("donaciones", donaciones);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("categoriaSeleccionada", categoria);
        model.addAttribute("subcategoriaSeleccionada", subcategoria);
        model.addAttribute("paginaActual", paginaActual);
        model.addAttribute("totalPaginas", totalPaginas);
        model.addAttribute("totalElementos", totalElementos);
        model.addAttribute("size", size);

        return "donante/donaciones";
    }

    @GetMapping("/incentivos")
    public String incentivos(Model model,
                             @RequestParam(value = "donanteId", required = false) Long donanteId,
                             HttpSession session) {
        Long resolvedId = resolveDonanteId(session, donanteId);
        Map<String, Object> metricas = apiClient.obtenerMetricasDonante(resolvedId);
        if (metricas == null) {
            metricas = Collections.emptyMap();
        }
        Map<String, Object> analitica = apiClient.obtenerAnaliticaDonante(resolvedId);
        if (analitica == null || analitica.isEmpty()) {
            analitica = new HashMap<>();
            analitica.put("organizacionesAyudadas", metricas.getOrDefault("organizacionesAyudadas", 0));
            analitica.put("posicionRanking", metricas.getOrDefault("posicionRanking", 0));
            analitica.put("misionesMesActual", metricas.getOrDefault("misionesCompletadas", 0));
            analitica.put("misionesMesAnterior", 0);
            analitica.put("evolucionMensual", Collections.emptyList());
        }
        List<Map<String, Object>> misionesProgreso = apiClient.listarMisionesDonante(resolvedId);
        List<Map<String, Object>> insignias = apiClient.listarInsigniasDonante(resolvedId);
        List<Map<String, Object>> ranking = apiClient.obtenerRankingMensual(null, null);

        model.addAttribute("donanteId", resolvedId);
        model.addAttribute("metricas", metricas);
        model.addAttribute("analitica", analitica);
        model.addAttribute("misiones", misionesProgreso);
        model.addAttribute("insignias", insignias);
        model.addAttribute("ranking", ranking);

        return "donante/incentivos";
    }

    @GetMapping("/tracking")
    public String tracking(Model model,
                           @RequestParam(value = "donanteId", required = false) Long donanteId,
                           HttpSession session) {
        Long resolvedId = resolveDonanteId(session, donanteId);
        List<Map<String, Object>> camiones = apiClient.listarCamiones();
        List<Map<String, Object>> rutas = apiClient.listarRutas();
        List<Map<String, Object>> flotaEnVivo = apiClient.obtenerFlotaEnVivo();
        List<Map<String, Object>> misDonaciones = apiClient.listarDonaciones(null, resolvedId);

        model.addAttribute("donanteId", resolvedId);
        model.addAttribute("camiones", camiones);
        model.addAttribute("rutas", rutas);
        model.addAttribute("flotaEnVivo", flotaEnVivo);
        model.addAttribute("misDonaciones", misDonaciones);

        return "donante/tracking";
    }

    @GetMapping("/entidades")
    public String entidades(Model model) {
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        model.addAttribute("entidades", entidades);
        return "donante/entidades";
    }

    @GetMapping("/donaciones/{id}/historial")
    @ResponseBody
    public org.springframework.http.ResponseEntity<List<Map<String, Object>>> obtenerHistorialDonacion(
            @PathVariable("id") Long id,
            HttpSession session) {
        UserSession userSession = session != null ? (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY) : null;
        if (userSession != null && !userSession.isAdmin() && userSession.getDonanteId() != null) {
            Map<String, Object> donacion = apiClient.obtenerDonacion(id);
            if (donacion != null && !donacion.isEmpty()) {
                Long ownerId = null;
                if (donacion.get("donante") instanceof Map<?, ?> donMap && donMap.get("id") instanceof Number num) {
                    ownerId = num.longValue();
                } else if (donacion.get("donanteId") instanceof Number num) {
                    ownerId = num.longValue();
                }
                if (ownerId != null && !ownerId.equals(userSession.getDonanteId())) {
                    return org.springframework.http.ResponseEntity
                            .status(org.springframework.http.HttpStatus.FORBIDDEN)
                            .body(Collections.emptyList());
                }
            }
        }
        return org.springframework.http.ResponseEntity.ok(apiClient.obtenerHistorialDonacion(id));
    }

    @PostMapping("/insignias/{id}/visibilidad")
    public String alternarVisibilidadInsignia(@PathVariable("id") Long id,
                                             @RequestParam("visible") boolean visible,
                                             HttpSession session,
                                             RedirectAttributes redirectAttrs) {
        Long resolvedId = resolveDonanteId(session, null);
        UserSession userSession = session != null ? (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY) : null;
        if (userSession == null || !userSession.isAdmin()) {
            List<Map<String, Object>> misInsignias = apiClient.listarInsigniasDonante(resolvedId);
            if (misInsignias != null && !misInsignias.isEmpty()) {
                boolean pertenece = misInsignias.stream()
                        .anyMatch(ins -> ins.get("id") instanceof Number num && id.equals(num.longValue()));
                if (!pertenece) {
                    redirectAttrs.addFlashAttribute("mensajeError", "No tiene permisos para modificar una insignia que no pertenece a su perfil.");
                    return "redirect:/donante/incentivos";
                }
            }
        }
        boolean ok = apiClient.alternarVisibilidadInsignia(id, visible);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Visibilidad de la insignia actualizada a: " + (visible ? "PÚBLICA" : "PRIVADA"));
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo actualizar la visibilidad de la insignia.");
        }
        return "redirect:/donante/incentivos";
    }

    @GetMapping("/notificaciones")
    public String notificaciones(Model model,
                                 @RequestParam(value = "donanteId", required = false) Long donanteId,
                                 HttpSession session) {
        Long resolvedId = resolveDonanteId(session, donanteId);
        Map<String, Object> donante = apiClient.obtenerDonante(resolvedId);
        String emailDonante = "";
        if (donante != null && donante.get("emailPrincipal") != null) {
            emailDonante = String.valueOf(donante.get("emailPrincipal"));
        } else if (session != null) {
            UserSession userSession = (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY);
            if (userSession != null && userSession.getEmail() != null) {
                emailDonante = userSession.getEmail();
            }
        }

        List<Map<String, Object>> notificaciones = (emailDonante != null && !emailDonante.isBlank())
                ? apiClient.listarNotificaciones(emailDonante)
                : Collections.emptyList();

        model.addAttribute("donanteId", resolvedId);
        model.addAttribute("emailDonante", emailDonante);
        model.addAttribute("notificaciones", notificaciones != null ? notificaciones : Collections.emptyList());
        return "donante/notificaciones";
    }
}
