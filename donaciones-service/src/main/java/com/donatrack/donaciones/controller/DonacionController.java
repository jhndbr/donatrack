package com.donatrack.donaciones.controller;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.BienDonado;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.HistorialEstadoDonacion;
import com.donatrack.donaciones.dto.CargaSegmentadaRequestDto;
import com.donatrack.donaciones.dto.DonacionMapaPublicoDto;
import com.donatrack.donaciones.matchmaking.MatchmakingRankingResult;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.service.DonacionService;
import com.donatrack.donaciones.service.MatchmakingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import com.donatrack.donaciones.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/donaciones")
public class DonacionController {

    private final DonacionService donacionService;
    private final MatchmakingService matchmakingService;
    private final DonacionRepository donacionRepository;
    private final FileStorageService fileStorageService;
    private final com.donatrack.donaciones.scheduler.DonacionesScheduler donacionesScheduler;
    private final com.donatrack.donaciones.matchmaking.CompatibilidadSemanticaStrategy semanticaStrategy;
    private final com.donatrack.donaciones.matchmaking.PrioridadSubatendidosStrategy subatendidosStrategy;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.donatrack.donaciones.repository.ConfiguracionMatchmakingRepository configuracionMatchmakingRepository;

    public DonacionController(DonacionService donacionService,
                              MatchmakingService matchmakingService,
                              DonacionRepository donacionRepository,
                              FileStorageService fileStorageService,
                              com.donatrack.donaciones.scheduler.DonacionesScheduler donacionesScheduler,
                              @org.springframework.beans.factory.annotation.Autowired(required = false) com.donatrack.donaciones.matchmaking.CompatibilidadSemanticaStrategy semanticaStrategy,
                              @org.springframework.beans.factory.annotation.Autowired(required = false) com.donatrack.donaciones.matchmaking.PrioridadSubatendidosStrategy subatendidosStrategy) {
        this.donacionService = donacionService;
        this.matchmakingService = matchmakingService;
        this.donacionRepository = donacionRepository;
        this.fileStorageService = fileStorageService;
        this.donacionesScheduler = donacionesScheduler;
        this.semanticaStrategy = semanticaStrategy;
        this.subatendidosStrategy = subatendidosStrategy;
    }

    /**
     * Requerimiento 7 de Entrega 1:
     * Saludo verificador expuesto por el microservicio de Donaciones.
     */
    @GetMapping("/saludo")
    public ResponseEntity<String> saludo() {
        return ResponseEntity.ok("Hola desde el servicio de Donaciones.");
    }

    @GetMapping
    public ResponseEntity<List<Donacion>> listarDonaciones(
            @RequestParam(value = "estado", required = false) EstadoDonacion estado,
            @RequestParam(value = "donanteId", required = false) Long donanteId) {
        if (donanteId != null && estado != null) {
            return ResponseEntity.ok(donacionService.filtrarDonaciones(List.of(estado), null, donanteId, null, null, null, null));
        }
        if (donanteId != null) {
            return ResponseEntity.ok(donacionRepository.findByDonanteId(donanteId));
        }
        if (estado != null) {
            return ResponseEntity.ok(donacionRepository.findByEstado(estado));
        }
        return ResponseEntity.ok(donacionRepository.findAll());
    }

    /**
     * Consulta paginada multicriterio de donaciones (CU-16, CU-17).
     */
    @GetMapping("/paginado")
    public ResponseEntity<org.springframework.data.domain.Page<Donacion>> listarDonacionesPaginadas(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "estado", required = false) EstadoDonacion estado,
            @RequestParam(value = "donanteId", required = false) Long donanteId,
            @RequestParam(value = "subcategoria", required = false) String subcategoria) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), Math.max(size, 1),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));

        List<EstadoDonacion> estados = estado != null ? List.of(estado) : null;
        org.springframework.data.jpa.domain.Specification<Donacion> spec =
                com.donatrack.donaciones.repository.specification.DonacionSpecification.conFiltros(
                        estados, subcategoria, donanteId, null, null, null, null);
        return ResponseEntity.ok(donacionRepository.findAll(spec, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Donacion> obtenerPorId(@PathVariable("id") Long id) {
        return donacionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HistorialEstadoDonacion>> obtenerHistorial(@PathVariable("id") Long id) {
        return donacionRepository.findById(id)
                .map(d -> ResponseEntity.ok(d.getHistorialEstados()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Endpoint público comunitario para visualización de donaciones entregadas en mapa (CU-03, CU-04).
     */
    @GetMapping("/publico/mapa")
    public ResponseEntity<List<DonacionMapaPublicoDto>> listarDonacionesPublicasParaMapa() {
        List<Donacion> entregadas = donacionRepository.findDonacionesEntregadasPublicas();
        List<DonacionMapaPublicoDto> dtos = entregadas.stream().map(d -> new DonacionMapaPublicoDto(
                d.getId(),
                d.getSubcategoria(),
                d.getCantidad(),
                d.getUnidadMedida(),
                d.isEsUsado(),
                d.getEntidadAsignada() != null ? d.getEntidadAsignada().getRazonSocial() : "Sede comunitaria",
                d.getEntidadAsignada() != null ? d.getEntidadAsignada().getDireccion() : "Sin dirección",
                d.getEntidadAsignada() != null && d.getEntidadAsignada().getLatitud() != null ? d.getEntidadAsignada().getLatitud() : -34.6037,
                d.getEntidadAsignada() != null && d.getEntidadAsignada().getLongitud() != null ? d.getEntidadAsignada().getLongitud() : -58.3816,
                d.getFechaActualizacionEstado(),
                d.getBienes().stream().map(BienDonado::getFotoUrl).filter(Objects::nonNull).toList()
        )).toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Requerimiento CU-13: Consulta de configuración y pesos de algoritmos de matchmaking.
     */
    @GetMapping("/matchmaking/config")
    public ResponseEntity<Map<String, Object>> obtenerConfigMatchmaking() {
        if (configuracionMatchmakingRepository != null) {
            var opt = configuracionMatchmakingRepository.findById(1L);
            if (opt.isPresent()) {
                var cfg = opt.get();
                return ResponseEntity.ok(Map.of(
                        "a1", cfg.getA1(),
                        "a2", cfg.getA2(),
                        "a3", cfg.getA3(),
                        "alpha", cfg.getAlpha()
                ));
            }
        }
        double a1 = semanticaStrategy != null ? semanticaStrategy.getA1() : 0.4;
        double a2 = semanticaStrategy != null ? semanticaStrategy.getA2() : 0.4;
        double a3 = semanticaStrategy != null ? semanticaStrategy.getA3() : 0.2;
        double alpha = subatendidosStrategy != null ? subatendidosStrategy.getAlpha() : 0.1;
        return ResponseEntity.ok(Map.of("a1", a1, "a2", a3 == 0 ? 0.4 : a2, "a3", a3, "alpha", alpha));
    }

    /**
     * Requerimiento CU-13: Parametrización dinámica de pesos a1, a2, a3 y factor alpha por el Administrador.
     */
    @PutMapping("/matchmaking/config")
    public ResponseEntity<Map<String, Object>> actualizarConfigMatchmaking(@RequestBody Map<String, Double> config) {
        double a1 = config.getOrDefault("a1", semanticaStrategy != null ? semanticaStrategy.getA1() : 0.4);
        double a2 = config.getOrDefault("a2", semanticaStrategy != null ? semanticaStrategy.getA2() : 0.4);
        double a3 = config.getOrDefault("a3", semanticaStrategy != null ? semanticaStrategy.getA3() : 0.2);
        double alpha = config.getOrDefault("alpha", subatendidosStrategy != null ? subatendidosStrategy.getAlpha() : 0.1);

        if (config.containsKey("a1") && config.containsKey("a2") && config.containsKey("a3") && semanticaStrategy != null) {
            semanticaStrategy.configurarPesos(a1, a2, a3);
        }
        if (config.containsKey("alpha") && subatendidosStrategy != null) {
            subatendidosStrategy.setAlpha(alpha);
        }
        if (configuracionMatchmakingRepository != null) {
            com.donatrack.donaciones.domain.ConfiguracionMatchmaking entidadCfg =
                    new com.donatrack.donaciones.domain.ConfiguracionMatchmaking(a1, a2, a3, alpha);
            configuracionMatchmakingRepository.save(entidadCfg);
        }
        return ResponseEntity.ok(Map.of("a1", a1, "a2", a2, "a3", a3, "alpha", alpha));
    }

    /**
     * Consulta y filtrado multicriterio dinámico mediante JPA Specifications (CU-16, CU-17).
     */
    @GetMapping("/filtrar")
    public ResponseEntity<List<Donacion>> filtrarDonaciones(
            @RequestParam(value = "estados", required = false) List<EstadoDonacion> estados,
            @RequestParam(value = "subcategoria", required = false) String subcategoria,
            @RequestParam(value = "donanteId", required = false) Long donanteId,
            @RequestParam(value = "entidadId", required = false) Long entidadId,
            @RequestParam(value = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(value = "esUsado", required = false) Boolean esUsado
    ) {
        return ResponseEntity.ok(donacionService.filtrarDonaciones(estados, subcategoria, donanteId, entidadId, desde, hasta, esUsado));
    }

    /**
     * Modificación de atributos descriptivos de una donación en depósito (CU-18).
     */
    @PutMapping("/{id}")
    public ResponseEntity<Donacion> modificarDonacion(@PathVariable("id") Long id,
                                                      @RequestBody Map<String, Object> body) {
        String subcategoria = (String) body.get("subcategoria");
        Double cantidad = body.containsKey("cantidad") ? ((Number) body.get("cantidad")).doubleValue() : null;
        String unidadMedida = (String) body.get("unidadMedida");
        Boolean esUsado = (Boolean) body.get("esUsado");

        Donacion modificada = donacionService.modificarDonacion(id, subcategoria, cantidad, unidadMedida, esUsado);
        return ResponseEntity.ok(modificada);
    }

    @PostMapping("/carga-segmentada")
    public ResponseEntity<List<Donacion>> registrarDonacionSegmentada(@RequestParam("donanteId") Long donanteId,
                                                                      @RequestParam(value = "entidadId", required = false) Long entidadId,
                                                                      @RequestBody List<BienDonado> bienes) {
        List<Donacion> donaciones = (entidadId != null)
                ? donacionService.registrarCargaSegmentada(donanteId, bienes, entidadId)
                : donacionService.registrarCargaSegmentada(donanteId, bienes);
        return ResponseEntity.ok(donaciones);
    }

    /**
     * Subida de imágenes locales al servidor con almacenamiento seguro y validación de tipos MIME.
     */
    @PostMapping(value = "/subir-imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> subirImagen(@RequestParam("archivo") MultipartFile archivo) {
        String nombreArchivo = fileStorageService.guardarArchivo(archivo);
        String urlDescarga = "/api/v1/donaciones/imagenes/" + nombreArchivo;
        return ResponseEntity.ok(Map.of(
                "url", urlDescarga,
                "nombreArchivo", nombreArchivo
        ));
    }

    /**
     * Endpoint para servir la imagen almacenada con encabezados de caché y Content-Type adecuado.
     */
    @GetMapping("/imagenes/{nombreArchivo:.+}")
    public ResponseEntity<Resource> servirImagen(@PathVariable("nombreArchivo") String nombreArchivo) {
        Resource recurso = fileStorageService.cargarArchivoComoRecurso(nombreArchivo);
        String contentType = fileStorageService.determinarContentType(nombreArchivo);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(recurso);
    }

    /**
     * Carga segmentada tipada con DTO inmutable y Bean Validation (CU-13, CU-14, CU-15).
     */
    @PostMapping("/carga-segmentada/dto")
    public ResponseEntity<List<Donacion>> registrarCargaSegmentadaDto(@Valid @RequestBody CargaSegmentadaRequestDto request) {
        List<BienDonado> bienes = request.bienes().stream()
                .map(b -> new BienDonado(b.descripcion(), b.categoria(), b.subcategoria(), b.cantidad(), b.unidadMedida(),
                        Boolean.TRUE.equals(b.esUsado()), b.fechaVencimiento(), b.fotoUrl()))
                .toList();
        List<Donacion> donaciones = donacionService.registrarCargaSegmentada(request.donanteId(), bienes);
        return ResponseEntity.ok(donaciones);
    }

    @GetMapping("/{id}/matchmaking")
    public ResponseEntity<MatchmakingRankingResult> ejecutarMatchmaking(@PathVariable("id") Long id) {
        return ResponseEntity.ok(matchmakingService.evaluarDonacion(id));
    }

    /**
     * Disparo inmediato del proceso batch de Matchmaking para asignación algorítmica de donaciones en depósito (CU-30 a CU-35).
     */
    @PostMapping("/matchmaking/ejecutar-batch")
    public ResponseEntity<Map<String, Object>> ejecutarMatchmakingBatchManual() {
        int asignadas = donacionesScheduler.procesarMatchmakingDonaciones();
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "mensaje", "Ejecución batch de matchmaking procesada exitosamente.",
                "donacionesAsignadas", asignadas
        ));
    }

    @GetMapping("/matchmaking/ejecutar-batch")
    public ResponseEntity<Map<String, Object>> ejecutarMatchmakingBatchManualGet() {
        return ejecutarMatchmakingBatchManual();
    }

    /**
     * Disparo manual del detector de donantes inactivos (>20 días) para pruebas y gestión administrativa (CU-57).
     */
    @PostMapping("/inactivos/detectar")
    public ResponseEntity<Map<String, Object>> detectarDonantesInactivosManual() {
        donacionesScheduler.detectarDonantesInactivos();
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "mensaje", "Detección de donantes inactivos ejecutada y eventos encolados en Outbox."
        ));
    }

    /**
     * Confirmación formal de asignación con entidad beneficiaria y necesidad material receptora (CU-34).
     */
    @PostMapping("/{id}/asignar")
    public ResponseEntity<Donacion> asignarDonacion(@PathVariable("id") Long id,
                                                    @RequestParam("entidadId") Long entidadId,
                                                    @RequestParam(value = "necesidadId", required = false) Long necesidadId,
                                                    @RequestParam(value = "usuario", required = false) String usuario) {
        Donacion asignada;
        if (necesidadId == null && usuario == null) {
            asignada = donacionService.asignarDonacion(id, entidadId);
        } else {
            asignada = donacionService.asignarDonacion(id, entidadId, necesidadId, usuario != null ? usuario : "OPERADOR");
        }
        return ResponseEntity.ok(asignada);
    }

    /**
     * Desasignación administrativa justificada para revertir a EN_DEPOSITO (CU-18).
     */
    @PostMapping("/{id}/desasignar")
    public ResponseEntity<Donacion> desasignarDonacion(@PathVariable("id") Long id,
                                                       @RequestBody Map<String, String> body) {
        String motivo = body.getOrDefault("motivo", "Desasignación administrativa");
        String usuario = body.getOrDefault("usuario", "ADMIN");
        Donacion desasignada = donacionService.desasignarDonacion(id, motivo, usuario);
        return ResponseEntity.ok(desasignada);
    }

    @RequestMapping(value = "/{id}/estado", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<Donacion> actualizarEstado(@PathVariable("id") Long id,
                                                     @RequestBody Map<String, String> request) {
        EstadoDonacion nuevoEstado = EstadoDonacion.valueOf(request.get("nuevoEstado"));
        String justificacion = request.get("justificacion");
        String usuario = request.get("usuario");
        String fotoUrl = request.get("fotoUrl");

        Donacion actualizada;
        if (usuario == null && fotoUrl == null) {
            actualizada = donacionService.actualizarEstado(id, nuevoEstado, justificacion);
        } else if (fotoUrl == null) {
            actualizada = donacionService.actualizarEstado(id, nuevoEstado, justificacion, usuario);
        } else {
            actualizada = donacionService.actualizarEstado(id, nuevoEstado, justificacion,
                    usuario != null ? usuario : "OPERADOR", fotoUrl);
        }
        return ResponseEntity.ok(actualizada);
    }

    /**
     * Requerimiento del TP: Actualizacion masiva de estado para lotes logisticos
     */
    @PutMapping("/bulk-estado")
    public ResponseEntity<List<Donacion>> actualizarEstadoLote(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("donacionIds");
        if (idsRaw == null || idsRaw.isEmpty()) {
            idsRaw = (List<Number>) body.get("idsDonaciones");
        }
        if (idsRaw == null || idsRaw.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<Long> ids = idsRaw.stream().map(Number::longValue).toList();
        EstadoDonacion nuevoEstado = EstadoDonacion.valueOf((String) body.get("nuevoEstado"));
        String justificacion = (String) body.get("justificacion");
        return ResponseEntity.ok(donacionService.actualizarEstadoLote(ids, nuevoEstado, justificacion));
    }

    /**
     * Endpoint para que logistica-service consulte donaciones listas para armar rutas en lotes
     */
    @GetMapping("/asignadas-logistica")
    public ResponseEntity<List<Donacion>> listarAsignadasParaLogistica() {
        return ResponseEntity.ok(donacionRepository.findAsignadasListasParaLogistica());
    }
}
