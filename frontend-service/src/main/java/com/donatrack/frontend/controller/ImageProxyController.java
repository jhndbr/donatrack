package com.donatrack.frontend.controller;

import com.donatrack.frontend.service.ApiClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controlador Proxy para servir recursos de imágenes estáticas de donaciones
 * directamente a través del Frontend Web SSR con resiliencia ante fallos.
 */
@RestController
public class ImageProxyController {

    private static final Logger log = LoggerFactory.getLogger(ImageProxyController.class);

    private static final String DEFAULT_SVG_PLACEHOLDER = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 400" width="600" height="400">
          <defs>
            <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#003D3F" />
              <stop offset="60%" stop-color="#005B60" />
              <stop offset="100%" stop-color="#007A8E" />
            </linearGradient>
            <linearGradient id="cardGrad" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.12" />
              <stop offset="100%" stop-color="#FFFFFF" stop-opacity="0.04" />
            </linearGradient>
          </defs>
          <rect width="600" height="400" fill="url(#bgGrad)" />
          <rect x="130" y="55" width="340" height="290" rx="20" fill="url(#cardGrad)" stroke="#D1F4F5" stroke-opacity="0.25" stroke-width="1.5" />
          <circle cx="300" cy="140" r="54" fill="#009B9F" fill-opacity="0.25" stroke="#009B9F" stroke-width="2" />
          <circle cx="300" cy="140" r="44" fill="#003D3F" />
          <g transform="translate(276, 116)" fill="none" stroke="#D1F4F5" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 14 L24 4 L44 14 L24 24 Z" fill="#007A8E" fill-opacity="0.5" />
            <path d="M4 14 L4 34 L24 44 L24 24 Z" fill="#005B60" fill-opacity="0.5" />
            <path d="M44 14 L44 34 L24 44 L24 24 Z" fill="#009B9F" fill-opacity="0.5" />
            <circle cx="36" cy="36" r="11" fill="#10B981" stroke="#FFFFFF" stroke-width="2" />
            <path d="M32 36 L35 39 L40 33" stroke="#FFFFFF" stroke-width="2.5" />
          </g>
          <text x="300" y="235" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-size="22" font-weight="700" fill="#FFFFFF" text-anchor="middle">
            DonaTrack
          </text>
          <text x="300" y="262" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-size="14" font-weight="600" fill="#D1F4F5" text-anchor="middle">
            Comprobante de Entrega Certificada
          </text>
          <rect x="200" y="285" width="200" height="26" rx="13" fill="#10B981" fill-opacity="0.2" stroke="#10B981" stroke-width="1" />
          <text x="300" y="302" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif" font-size="11" font-weight="600" fill="#A7F3D0" text-anchor="middle">
            ✓ Evidencia de Recepción Institucional
          </text>
        </svg>
        """;

    private final ApiClientService apiClient;

    public ImageProxyController(ApiClientService apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping({"/api/v1/donaciones/imagenes/{nombreArchivo:.+}", "/uploads/{nombreArchivo:.+}"})
    public ResponseEntity<byte[]> servirImagenDonacion(@PathVariable("nombreArchivo") String nombreArchivo) {
        String archivoLimpio = nombreArchivo != null && nombreArchivo.startsWith("donaciones/")
                ? nombreArchivo.substring("donaciones/".length())
                : nombreArchivo;

        // 1. Intentar obtener vía API Client (Gateway -> Donaciones Service)
        try {
            ResponseEntity<byte[]> response = apiClient.obtenerImagenDonacion(archivoLimpio);
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                MediaType contentType = response.getHeaders().getContentType();
                if (contentType == null) {
                    contentType = determinarContentType(archivoLimpio);
                }
                return ResponseEntity.ok()
                        .contentType(contentType)
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                        .body(response.getBody());
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener imagen vía API para {}: {}", archivoLimpio, e.getMessage());
        }

        // 2. Intentar buscar directamente en disco en uploads/donaciones o uploads/
        byte[] bytesDisco = buscarEnDisco(archivoLimpio);
        if (bytesDisco != null) {
            return ResponseEntity.ok()
                    .contentType(determinarContentType(archivoLimpio))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(bytesDisco);
        }

        // 3. Fallback: Servir SVG placeholder oficial de DonaTrack
        return servirPlaceholder();
    }

    @GetMapping(value = "/images/placeholder-entrega.svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> servirPlaceholderDirecto() {
        return servirPlaceholder();
    }

    private ResponseEntity<byte[]> servirPlaceholder() {
        byte[] svgBytes = obtenerSvgPlaceholder();
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml;charset=UTF-8"))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(svgBytes);
    }

    private byte[] buscarEnDisco(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.contains("..")) {
            return null;
        }
        Path[] rutasPosibles = new Path[]{
                Paths.get("uploads/donaciones", nombreArchivo),
                Paths.get("uploads", nombreArchivo),
                Paths.get("../uploads/donaciones", nombreArchivo),
                Paths.get("../uploads", nombreArchivo)
        };
        for (Path ruta : rutasPosibles) {
            if (Files.exists(ruta) && Files.isReadable(ruta) && !Files.isDirectory(ruta)) {
                try {
                    return Files.readAllBytes(ruta);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private byte[] obtenerSvgPlaceholder() {
        try {
            ClassPathResource resource = new ClassPathResource("static/images/placeholder-entrega.svg");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return is.readAllBytes();
                }
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_SVG_PLACEHOLDER.getBytes(StandardCharsets.UTF_8);
    }

    private MediaType determinarContentType(String filename) {
        if (filename == null) return MediaType.IMAGE_JPEG;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.valueOf("image/webp");
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        return MediaType.IMAGE_JPEG;
    }
}
