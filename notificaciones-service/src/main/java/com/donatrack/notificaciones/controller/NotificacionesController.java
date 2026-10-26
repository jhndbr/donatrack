package com.donatrack.notificaciones.controller;

import com.donatrack.common.enums.CanalNotificacion;
import com.donatrack.notificaciones.domain.AuditoriaNotificacion;
import com.donatrack.notificaciones.domain.EstadoNotificacion;
import com.donatrack.notificaciones.repository.AuditoriaNotificacionRepository;
import com.donatrack.notificaciones.service.NotificacionesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para auditoría, trazabilidad y métricas del servicio de Notificaciones (CU-56).
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionesController {

    private final AuditoriaNotificacionRepository auditoriaRepo;
    private final NotificacionesService notificacionesService;

    public NotificacionesController(AuditoriaNotificacionRepository auditoriaRepo,
                                    NotificacionesService notificacionesService) {
        this.auditoriaRepo = auditoriaRepo;
        this.notificacionesService = notificacionesService;
    }

    /**
     * Consulta el historial de notificaciones y trazabilidad de envíos (CU-56).
     */
    @GetMapping("/historial")
    public ResponseEntity<List<AuditoriaNotificacion>> obtenerHistorial(
            @RequestParam(value = "destinatario", required = false) String destinatario,
            @RequestParam(value = "canal", required = false) CanalNotificacion canal,
            @RequestParam(value = "estado", required = false) EstadoNotificacion estado) {

        if (destinatario != null && !destinatario.isBlank()) {
            List<AuditoriaNotificacion> list = auditoriaRepo.findByDestinatarioOrderByTimestampDesc(destinatario);
            if (list == null || list.isEmpty()) {
                list = auditoriaRepo.findByDestinatarioIgnoreCaseOrderByTimestampDesc(destinatario.trim());
            }
            return ResponseEntity.ok(list);
        }
        if (canal != null) {
            return ResponseEntity.ok(auditoriaRepo.findByCanalOrderByTimestampDesc(canal));
        }
        if (estado != null) {
            return ResponseEntity.ok(auditoriaRepo.findByEstadoOrderByTimestampDesc(estado));
        }
        return ResponseEntity.ok(auditoriaRepo.findTop50ByOrderByTimestampDesc());
    }

    /**
     * Consulta la cantidad de notificaciones no leídas para mostrar el indicador numérico (badge rojo) en la barra de navegación.
     */
    @GetMapping("/no-leidas/count")
    public ResponseEntity<Map<String, Object>> obtenerConteoNoLeidas(
            @RequestParam(value = "destinatario", required = false) String destinatario) {
        long count;
        if (destinatario != null && !destinatario.isBlank()) {
            count = auditoriaRepo.countByDestinatarioIgnoreCaseAndLeidaFalse(destinatario.trim());
        } else {
            count = auditoriaRepo.countByLeidaFalse();
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("destinatario", destinatario != null ? destinatario : "ALL");
        resp.put("noLeidas", count);
        return ResponseEntity.ok(resp);
    }

    /**
     * Marca una notificación individual como leída.
     */
    @PostMapping("/{id}/leer")
    public ResponseEntity<Map<String, Object>> marcarComoLeida(@PathVariable("id") Long id) {
        return auditoriaRepo.findById(id)
                .map(notif -> {
                    notif.marcarComoLeida();
                    auditoriaRepo.save(notif);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("id", notif.getId());
                    resp.put("leida", notif.isLeida());
                    resp.put("fechaLectura", notif.getFechaLectura());
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Marca todas las notificaciones de un destinatario (o todas las del sistema si no se envía destinatario) como leídas.
     */
    @PostMapping("/leer-todas")
    public ResponseEntity<Map<String, Object>> marcarTodasComoLeidas(
            @RequestParam(value = "destinatario", required = false) String destinatario) {
        List<AuditoriaNotificacion> pendientes;
        if (destinatario != null && !destinatario.isBlank()) {
            pendientes = auditoriaRepo.findByDestinatarioIgnoreCaseAndLeidaFalse(destinatario.trim());
        } else {
            pendientes = auditoriaRepo.findByLeidaFalse();
        }
        if (pendientes != null && !pendientes.isEmpty()) {
            for (AuditoriaNotificacion n : pendientes) {
                n.marcarComoLeida();
            }
            auditoriaRepo.saveAll(pendientes);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("marcadas", pendientes != null ? pendientes.size() : 0);
        resp.put("destinatario", destinatario != null ? destinatario : "ALL");
        return ResponseEntity.ok(resp);
    }

    /**
     * Consulta estadísticas agregadas de auditoría de comunicaciones (CU-56).
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<Map<String, Object>> obtenerEstadisticas() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRegistros", auditoriaRepo.count());
        stats.put("enviados", auditoriaRepo.countByEstado(EstadoNotificacion.ENVIADO));
        stats.put("fallidos", auditoriaRepo.countByEstado(EstadoNotificacion.FALLIDO));
        stats.put("duplicadosIgnorados", auditoriaRepo.countByEstado(EstadoNotificacion.DUPLICADO_IGNORADO));
        stats.put("noLeidas", auditoriaRepo.countByLeidaFalse());

        Map<String, Long> porCanal = new LinkedHashMap<>();
        for (CanalNotificacion c : CanalNotificacion.values()) {
            porCanal.put(c.name(), auditoriaRepo.countByCanal(c));
        }
        stats.put("porCanal", porCanal);

        return ResponseEntity.ok(stats);
    }

    /**
     * Envío manual de prueba para verificación operativa de canales.
     * Soporta tanto query params (?canal=...&destinatario=...) como JSON body ({"canal": ..., "destinatario": ...}).
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> enviarNotificacionManual(
            @RequestParam(value = "canal", required = false) CanalNotificacion canalParam,
            @RequestParam(value = "destinatario", required = false) String destinatarioParam,
            @RequestParam(value = "asunto", required = false) String asuntoParam,
            @RequestParam(value = "mensaje", required = false) String mensajeParam,
            @RequestBody(required = false) Map<String, Object> body) {

        CanalNotificacion canal = canalParam;
        String destinatario = destinatarioParam;
        String asunto = asuntoParam;
        String mensaje = mensajeParam;

        if (body != null) {
            if (canal == null && body.containsKey("canal")) {
                try {
                    canal = CanalNotificacion.valueOf(String.valueOf(body.get("canal")).trim().toUpperCase());
                } catch (Exception ignored) {
                    canal = CanalNotificacion.EMAIL;
                }
            }
            if (destinatario == null && body.containsKey("destinatario")) {
                destinatario = String.valueOf(body.get("destinatario"));
            }
            if (asunto == null && body.containsKey("asunto")) {
                asunto = String.valueOf(body.get("asunto"));
            }
            if (mensaje == null && body.containsKey("mensaje")) {
                mensaje = String.valueOf(body.get("mensaje"));
            }
        }

        if (canal == null) canal = CanalNotificacion.EMAIL;
        if (destinatario == null) destinatario = "";
        if (asunto == null) asunto = "Notificación de Prueba DonaTrack";
        if (mensaje == null) mensaje = "Mensaje de prueba";

        String claveManual = "test-manual-" + System.currentTimeMillis();
        boolean exito = notificacionesService.despacharConAuditoria(claveManual, "TEST_MANUAL", canal, destinatario, asunto, mensaje);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("exito", exito);
        resp.put("destinatario", destinatario);
        resp.put("canal", canal.name());
        resp.put("claveEvento", claveManual);

        return ResponseEntity.ok(resp);
    }
}
