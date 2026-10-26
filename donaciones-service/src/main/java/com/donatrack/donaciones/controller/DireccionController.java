package com.donatrack.donaciones.controller;

import com.donatrack.donaciones.dto.DireccionNormalizadaDto;
import com.donatrack.donaciones.service.NormalizacionDireccionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST para consultar, verificar y normalizar direcciones
 * en vivo mediante el servicio oficial Georef AR.
 */
@RestController
@RequestMapping({"/api/v1/donaciones/direcciones", "/api/v1/direcciones"})
public class DireccionController {

    private final NormalizacionDireccionService normalizacionService;

    public DireccionController(NormalizacionDireccionService normalizacionService) {
        this.normalizacionService = normalizacionService;
    }

    @GetMapping("/normalizar")
    public ResponseEntity<DireccionNormalizadaDto> normalizarDireccion(@RequestParam("direccion") String direccion) {
        DireccionNormalizadaDto resultado = normalizacionService.evaluarDireccion(direccion);
        return ResponseEntity.ok(resultado);
    }
}
