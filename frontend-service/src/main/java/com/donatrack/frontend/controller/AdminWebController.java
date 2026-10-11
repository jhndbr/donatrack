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

    @GetMapping("/matchmaking")
    public String matchmaking(Model model, @RequestParam(value = "donacionId", required = false) Long donacionId) {
        List<Map<String, Object>> pendientes = apiClient.listarDonaciones("EN_DEPOSITO");
        model.addAttribute("donacionesPendientes", pendientes);
        model.addAttribute("entidades", apiClient.listarEntidades());

        if (donacionId != null) {
            Map<String, Object> evaluacion = apiClient.evaluarMatchmaking(donacionId);
            model.addAttribute("donacionSeleccionada", donacionId);
            model.addAttribute("evaluacion", evaluacion);
        }

        return "admin/matchmaking";
    }

    @PostMapping("/matchmaking/asignar")
    public String confirmarAsignacion(@RequestParam("donacionId") Long donacionId,
                                      @RequestParam("entidadId") Long entidadId) {
        apiClient.asignarDonacion(donacionId, entidadId);
        return "redirect:/admin/matchmaking?asignada=ok";
    }

    @GetMapping("/importar-csv")
    public String importarCsvForm() {
        return "admin/importar_csv";
    }

    @PostMapping("/importar-csv")
    public String procesarCsv(@RequestParam("file") MultipartFile file, Model model) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "donantes.csv";
                }
            };
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            String url = apiClient.getGatewayUrl() + "/api/v1/donantes/importar-csv";
            @SuppressWarnings("unchecked")
            Map<String, Object> resumen = restTemplate.postForObject(url, requestEntity, Map.class);

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

    @PostMapping("/donaciones/nueva")
    public String guardarDonacionSegmentada(@RequestParam("donanteId") Long donanteId,
                                            @RequestParam("nombreBien") String nombreBien,
                                            @RequestParam("subcategoria") String subcategoria,
                                            @RequestParam("cantidad") double cantidad,
                                            @RequestParam("unidadMedida") String unidadMedida,
                                            @RequestParam(value = "esUsado", defaultValue = "false") boolean esUsado) {
        Map<String, Object> bien = Map.of(
                "nombre", nombreBien,
                "categoria", "Bienes Generales",
                "subcategoria", subcategoria,
                "cantidad", cantidad,
                "unidadMedida", unidadMedida,
                "esUsado", esUsado
        );

        apiClient.crearDonacionSegmentada(donanteId, List.of(bien));
        return "redirect:/admin";
    }
}
