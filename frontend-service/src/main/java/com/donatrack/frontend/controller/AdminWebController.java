package com.donatrack.frontend.controller;

import com.donatrack.frontend.service.ApiClientService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminWebController {

    private final ApiClientService apiClient;
    private final RestTemplate restTemplate;

    public AdminWebController(ApiClientService apiClient, RestTemplate restTemplate) {
        this.apiClient = apiClient;
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public String dashboard(Model model) {
        List<Map<String, Object>> donaciones = apiClient.listarDonaciones(null);
        List<Map<String, Object>> camiones = apiClient.listarCamiones();
        List<Map<String, Object>> entidades = apiClient.listarEntidades();
        List<Map<String, Object>> donantes = apiClient.listarDonantes();

        model.addAttribute("totalDonaciones", donaciones.size());
        model.addAttribute("totalCamiones", camiones.size());
        model.addAttribute("totalEntidades", entidades.size());
        model.addAttribute("totalDonantes", donantes.size());
        model.addAttribute("donacionesPendientes", donaciones.stream()
                .filter(d -> "EN_DEPOSITO".equals(d.get("estado")))
                .toList());
        model.addAttribute("donacionesFallidas", donaciones.stream()
                .filter(d -> "ENTREGA_FALLIDA".equals(d.get("estado")))
                .toList());

        return "admin/dashboard";
    }

    @GetMapping("/camiones")
    public String camiones(Model model) {
        List<Map<String, Object>> lista = apiClient.listarCamiones();
        model.addAttribute("camiones", lista);
        return "admin/camiones";
    }

    @PostMapping("/camiones")
    public String registrarCamion(@RequestParam("patente") String patente,
                                  @RequestParam("capacidadVolumenM3") double capacidadVolumenM3,
                                  @RequestParam("alturaM") double alturaM,
                                  @RequestParam("capacidadPesoKg") double capacidadPesoKg,
                                  Model model) {
        Map<String, Object> body = Map.of(
                "patente", patente,
                "capacidadVolumenM3", capacidadVolumenM3,
                "alturaM", alturaM,
                "capacidadPesoKg", capacidadPesoKg
        );
        apiClient.registrarCamion(body);
        return "redirect:/admin/camiones";
    }

    @PostMapping("/camiones/{id}/baja")
    public String darDeBajaCamion(@PathVariable("id") String id,
                                  @RequestParam(value = "motivo", defaultValue = "Baja técnica de unidad de transporte") String motivo,
                                  RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.darDeBajaCamion(id, motivo);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Camión " + id + " dado de baja exitosamente en la flota de logística.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo dar de baja el camión " + id + ". Verifique si posee rutas activas.");
        }
        return "redirect:/admin/camiones";
    }

    @PostMapping("/camiones/{id}/actualizar")
    public String actualizarCamion(@PathVariable("id") String id,
                                   @RequestParam(value = "patente", required = false) String patente,
                                   @RequestParam(value = "capacidadVolumenM3", required = false) Double capacidadVolumenM3,
                                   @RequestParam(value = "alturaM", required = false) Double alturaM,
                                   @RequestParam(value = "capacidadPesoKg", required = false) Double capacidadPesoKg,
                                   @RequestParam(value = "activo", required = false) Boolean activo,
                                   @RequestParam(value = "estado", required = false) String estado,
                                   RedirectAttributes redirectAttrs) {
        Map<String, Object> body = new java.util.HashMap<>();
        if (patente != null && !patente.isBlank()) body.put("patente", patente);
        if (capacidadVolumenM3 != null) body.put("capacidadVolumenM3", capacidadVolumenM3);
        if (alturaM != null) body.put("alturaM", alturaM);
        if (capacidadPesoKg != null) body.put("capacidadPesoKg", capacidadPesoKg);
        if (activo != null) body.put("activo", activo);
        if (estado != null && !estado.isBlank()) body.put("estado", estado);

        boolean ok = apiClient.actualizarCamion(id, body);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Camión " + id + " actualizado exitosamente.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo actualizar el camión " + id + ".");
        }
        return "redirect:/admin/camiones";
    }

    @GetMapping("/matchmaking")
    public String matchmaking(Model model, @RequestParam(value = "donacionId", required = false) Long donacionId) {
        List<Map<String, Object>> pendientes = apiClient.listarDonaciones("EN_DEPOSITO");
        List<Map<String, Object>> asignadas = apiClient.listarDonaciones("ASIGNACION_REALIZADA");
        List<Map<String, Object>> entidades = apiClient.listarEntidades();

        model.addAttribute("donacionesPendientes", pendientes);
        model.addAttribute("donacionesAsignadas", asignadas);
        model.addAttribute("entidades", entidades);

        if (donacionId != null) {
            Map<String, Object> evaluacion = apiClient.evaluarMatchmaking(donacionId);
            Map<String, Object> donacionDetalle = apiClient.obtenerDonacion(donacionId);
            model.addAttribute("donacionSeleccionada", donacionId);
            model.addAttribute("donacionDetalle", donacionDetalle);
            model.addAttribute("evaluacion", evaluacion);
        }

        return "admin/matchmaking";
    }

    @PostMapping("/matchmaking/asignar")
    public String asignarDonacion(@RequestParam("donacionId") Long donacionId,
                                  @RequestParam("entidadId") Long entidadId,
                                  @RequestParam(value = "necesidadId", required = false) Long necesidadId,
                                  RedirectAttributes redirectAttrs) {
        boolean ok = (necesidadId != null)
                ? (apiClient.asignarDonacion(donacionId, entidadId, necesidadId) || apiClient.asignarDonacion(donacionId, entidadId))
                : apiClient.asignarDonacion(donacionId, entidadId);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Donación DON-" + String.format("%04d", donacionId) + " asignada exitosamente a la entidad seleccionada!");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo asignar la donación DON-" + String.format("%04d", donacionId) +
                    ". Es posible que el motor automático ya la haya asignado a otra entidad o no se encuentre en depósito.");
        }
        return "redirect:/admin/matchmaking";
    }

    @PostMapping("/matchmaking/desasignar")
    public String desasignarDonacion(@RequestParam("donacionId") Long donacionId,
                                     @RequestParam(value = "motivo", required = false) String motivo,
                                     RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.desasignarDonacion(donacionId, motivo != null ? motivo : "Reingreso a depósito para reasignación");
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Donación DON-" + String.format("%04d", donacionId) + " desasignada exitosamente y reingresada a depósito!");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo desasignar la donación DON-" + String.format("%04d", donacionId) + ".");
        }
        return "redirect:/admin/matchmaking";
    }

    @GetMapping("/importar-csv")
    public String importarCsvForm() {
        return "admin/importar_csv";
    }

    @PostMapping("/importar-csv")
    public String procesarCsv(@RequestParam("file") MultipartFile file, Model model) {
        try {
            Map<String, Object> resumen = apiClient.importarCsvDonantes(file);
            if (resumen == null) {
                HttpHeaders headers = apiClient.buildAuthHeaders();
                if (headers == null) {
                    headers = new HttpHeaders();
                }
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", file.getResource());

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                String url = apiClient.getGatewayUrl() + "/api/v1/donantes/importar-csv";
                @SuppressWarnings("unchecked")
                Map<String, Object> fallbackResumen = restTemplate.postForObject(url, requestEntity, Map.class);
                resumen = fallbackResumen;
            }

            model.addAttribute("resumen", resumen);
            model.addAttribute("exito", true);
        } catch (Exception e) {
            model.addAttribute("error", "Error procesando el archivo CSV: " + e.getMessage());
        }
        return "admin/importar_csv";
    }

    @GetMapping("/rankings")
    public String rankings(Model model,
                           @RequestParam(value = "anio", required = false) Integer anio,
                           @RequestParam(value = "mes", required = false) Integer mes) {
        List<Map<String, Object>> ranking = apiClient.obtenerRankingMensual(anio, mes);
        model.addAttribute("rankings", ranking);
        model.addAttribute("anio", anio != null ? anio : 2026);
        model.addAttribute("mes", mes != null ? mes : 10);
        return "admin/rankings";
    }

    @GetMapping("/donaciones/nueva")
    public String nuevaDonacionForm(Model model) {
        model.addAttribute("donantes", apiClient.listarDonantes());
        return "admin/nueva_donacion";
    }

    @PostMapping("/donantes/registrar")
    public String registrarDonanteDesdeAdmin(@RequestParam(value = "tipoDonante", defaultValue = "HUMANA") String tipoDonante,
                                             @RequestParam(value = "nombre", required = false) String nombre,
                                             @RequestParam(value = "apellido", required = false) String apellido,
                                             @RequestParam(value = "dni", required = false) String dni,
                                             @RequestParam(value = "edad", defaultValue = "30") Integer edad,
                                             @RequestParam(value = "razonSocial", required = false) String razonSocial,
                                             @RequestParam(value = "cuit", required = false) String cuit,
                                             @RequestParam(value = "tipoOrganizacion", defaultValue = "Empresa") String tipoOrganizacion,
                                             @RequestParam(value = "rubro", defaultValue = "General") String rubro,
                                             @RequestParam(value = "representante", required = false) String representante,
                                             @RequestParam("email") String email,
                                             @RequestParam(value = "telefono", required = false) String telefono,
                                             @RequestParam(value = "direccion", required = false) String direccion,
                                             RedirectAttributes redirectAttrs) {
        java.util.List<Map<String, Object>> mediosContacto = new java.util.ArrayList<>();
        mediosContacto.add(Map.of("tipo", "EMAIL", "valor", email, "esPredeterminado", true));
        if (telefono != null && !telefono.trim().isEmpty()) {
            mediosContacto.add(Map.of("tipo", "SMS", "valor", telefono.trim(), "esPredeterminado", false));
        }
        String dirFinal = (direccion != null && !direccion.isBlank()) ? direccion.trim() : "Sin dirección";

        Map<String, Object> creado;
        if ("JURIDICA".equalsIgnoreCase(tipoDonante)) {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("razonSocial", razonSocial != null ? razonSocial : "Organización Donante");
            body.put("cuit", cuit != null ? cuit : "");
            body.put("tipo", tipoOrganizacion);
            body.put("rubro", rubro);
            body.put("direccion", dirFinal);
            body.put("mediosContacto", mediosContacto);
            if (representante != null && !representante.trim().isEmpty()) {
                body.put("representantes", List.of(representante.trim()));
            }
            creado = apiClient.crearDonanteJuridica(body);
        } else {
            Map<String, Object> body = Map.of(
                    "nombre", nombre != null ? nombre : "Donante",
                    "apellido", apellido != null ? apellido : "",
                    "nroDocumento", dni != null ? dni : "",
                    "edad", edad != null ? edad : 30,
                    "genero", "No especificado",
                    "direccion", dirFinal,
                    "mediosContacto", mediosContacto
            );
            creado = apiClient.crearDonanteHumana(body);
        }

        if (creado != null) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Donante registrado exitosamente en el sistema.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar el donante. Verifique los datos.");
        }
        return "redirect:/admin/donaciones/nueva";
    }

    @PostMapping("/donaciones/nueva")
    public String guardarDonacionSegmentada(@RequestParam("donanteId") Long donanteId,
                                            @RequestParam("nombreBien") List<String> nombresBien,
                                            @RequestParam("subcategoria") List<String> subcategorias,
                                            @RequestParam("cantidad") List<Double> cantidades,
                                            @RequestParam("unidadMedida") List<String> unidadesMedida,
                                            @RequestParam(value = "esUsado", required = false) List<Boolean> esUsados,
                                            @RequestParam(value = "fechaVencimiento", required = false) List<String> fechasVencimiento,
                                            @RequestParam(value = "descripcionGeneral", required = false) String descripcionGeneral,
                                            RedirectAttributes redirectAttrs) {
        java.util.List<Map<String, Object>> listaBienes = new java.util.ArrayList<>();
        int totalItems = subcategorias != null ? subcategorias.size() : 0;
        for (int i = 0; i < totalItems; i++) {
            String subcat = subcategorias.get(i);
            String nombre = (nombresBien != null && i < nombresBien.size()) ? nombresBien.get(i) : "";
            double cant = (cantidades != null && i < cantidades.size() && cantidades.get(i) != null) ? cantidades.get(i) : 1.0;
            String unidad = (unidadesMedida != null && i < unidadesMedida.size()) ? unidadesMedida.get(i) : "UNIDADES";
            boolean usado = (esUsados != null && i < esUsados.size() && Boolean.TRUE.equals(esUsados.get(i)));
            String fechaVenc = (fechasVencimiento != null && i < fechasVencimiento.size()) ? fechasVencimiento.get(i) : null;

            String descItem = (nombre != null && !nombre.isBlank()) ? nombre : (subcat + " (" + cant + " " + unidad + ")");
            if (descripcionGeneral != null && !descripcionGeneral.isBlank() && !descItem.contains(descripcionGeneral)) {
                descItem = descItem + " - " + descripcionGeneral.trim();
            }

            Map<String, Object> bien = new java.util.HashMap<>();
            bien.put("descripcion", descItem);
            bien.put("categoria", "Bienes Generales");
            bien.put("subcategoria", subcat);
            bien.put("cantidad", cant);
            bien.put("unidadMedida", unidad);
            bien.put("esUsado", usado);
            if (fechaVenc != null && !fechaVenc.isBlank()) {
                bien.put("fechaVencimiento", fechaVenc.trim());
            }
            listaBienes.add(bien);
        }

        apiClient.crearDonacionSegmentada(donanteId, listaBienes);
        redirectAttrs.addFlashAttribute("mensajeExito", "Carga de donación registrada y segmentada en depósito (" + listaBienes.size() + " ítem(s)).");
        return "redirect:/admin";
    }

    @PostMapping("/donantes/{id}/baja")
    public String darDeBajaDonante(@PathVariable("id") Long id,
                                   @RequestParam(value = "motivo", defaultValue = "Baja solicitada por administración") String motivo,
                                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.darDeBajaDonante(id, motivo);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Donante #" + id + " dado de baja lógicamente con éxito.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo dar de baja al donante #" + id + ". Verifique si posee donaciones activas.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/entidades/{id}/baja")
    public String darDeBajaEntidad(@PathVariable("id") Long id,
                                   @RequestParam(value = "motivo", defaultValue = "Baja administrativa") String motivo,
                                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.darDeBajaEntidad(id, motivo);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Entidad #" + id + " dada de baja lógicamente con éxito.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo dar de baja a la entidad #" + id + ". Verifique si posee donaciones en tránsito.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/donaciones/{id}/reingresar")
    public String reingresarDonacion(@PathVariable("id") Long id,
                                     @RequestParam(value = "motivo", defaultValue = "Reingreso físico a depósito tras entrega fallida") String motivo,
                                     org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        apiClient.replanificarEntregaPorDonacion(id, motivo);
        boolean ok = apiClient.actualizarEstadoDonacion(id, "EN_DEPOSITO", motivo);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Donación DON-" + String.format("%04d", id) + " reingresada exitosamente a depósito (CU-45).");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo reingresar la donación DON-" + String.format("%04d", id) + ".");
        }
        return "redirect:/admin";
    }

    @PostMapping("/donaciones/{id}/marcar-vencida")
    public String marcarDonacionVencida(@PathVariable("id") Long id,
                                        @RequestParam(value = "motivo", defaultValue = "Vencimiento o caducidad del bien") String motivo,
                                        RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.actualizarEstadoDonacion(id, "VENCIDA", motivo);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito", "Donación DON-" + String.format("%04d", id) + " marcada como VENCIDA exitosamente.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo marcar como vencida la donación DON-" + String.format("%04d", id) + ".");
        }
        return "redirect:/admin";
    }

    @PostMapping("/matchmaking/ejecutar")
    public String ejecutarMatchmakingManual(org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        Map<String, Object> res = apiClient.ejecutarMatchmakingBatch();
        Object asignadas = (res != null && res.containsKey("donacionesAsignadas")) ? res.get("donacionesAsignadas") : "0";
        redirectAttrs.addFlashAttribute("mensajeExito",
                "¡Proceso de Matchmaking ejecutado exitosamente! Donaciones asignadas en esta corrida: " + asignadas + ".");
        return "redirect:/admin";
    }

    @GetMapping("/notificaciones")
    public String notificaciones(Model model,
                                 @RequestParam(value = "destinatario", required = false) String destinatario) {
        Map<String, Object> estadisticas = apiClient.obtenerEstadisticasNotificaciones();
        List<Map<String, Object>> notificaciones = apiClient.listarNotificaciones(destinatario);
        List<Map<String, Object>> listaFinal = notificaciones != null ? notificaciones : List.of();
        long noLeidasEnLista = listaFinal.stream()
                .filter(n -> !Boolean.TRUE.equals(n.get("leida")))
                .count();

        model.addAttribute("estadisticas", estadisticas);
        model.addAttribute("notificaciones", listaFinal);
        model.addAttribute("destinatarioFiltro", destinatario != null ? destinatario : "");
        model.addAttribute("notificacionesNoLeidasVista", noLeidasEnLista);
        if (noLeidasEnLista > 0) {
            model.addAttribute("notificacionesNoLeidasCount", noLeidasEnLista);
        }
        return "admin/notificaciones";
    }

    @PostMapping("/notificaciones/{id}/leer")
    public String marcarNotificacionLeida(@PathVariable("id") Long id,
                                          @RequestParam(value = "destinatario", required = false) String destinatario,
                                          RedirectAttributes redirectAttrs) {
        apiClient.marcarNotificacionLeida(id);
        redirectAttrs.addFlashAttribute("mensajeExito", "Registro de notificación #" + id + " marcado como leído.");
        return "redirect:/admin/notificaciones" + (destinatario != null && !destinatario.isBlank() ? "?destinatario=" + destinatario : "");
    }

    @PostMapping("/notificaciones/leer-todas")
    public String marcarTodasNotificacionesLeidas(@RequestParam(value = "destinatario", required = false) String destinatario,
                                                  RedirectAttributes redirectAttrs) {
        apiClient.marcarTodasNotificacionesLeidas(destinatario);
        redirectAttrs.addFlashAttribute("mensajeExito", "Todas las notificaciones seleccionadas han sido marcadas como leídas.");
        return "redirect:/admin/notificaciones" + (destinatario != null && !destinatario.isBlank() ? "?destinatario=" + destinatario : "");
    }

    @PostMapping("/notificaciones/test")
    public String enviarNotificacionPrueba(@RequestParam("canal") String canal,
                                           @RequestParam("destinatario") String destinatario,
                                           @RequestParam("asunto") String asunto,
                                           @RequestParam("mensaje") String mensaje,
                                           RedirectAttributes redirectAttrs) {
        boolean ok = apiClient.enviarNotificacionPrueba(canal, destinatario, asunto, mensaje);
        if (ok) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "Notificación de prueba enviada exitosamente por canal " + canal + " a " + destinatario + ". Revise Mailpit (:8025) y la tabla de auditoría.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError",
                    "Error al enviar notificación de prueba a " + destinatario + ".");
        }
        return "redirect:/admin/notificaciones";
    }

    @PostMapping("/notificaciones/inactivos")
    public String detectarDonantesInactivos(RedirectAttributes redirectAttrs) {
        Map<String, Object> res = apiClient.detectarDonantesInactivos();
        if (res != null && !res.isEmpty()) {
            redirectAttrs.addFlashAttribute("mensajeExito",
                    "Barrido de donantes inactivos ejecutado (CU-19). Se publicaron los eventos donante.inactivo correspondientes en el Outbox.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError",
                    "No se pudo completar el barrido de donantes inactivos.");
        }
        return "redirect:/admin/notificaciones";
    }
}
