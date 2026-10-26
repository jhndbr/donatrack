package com.donatrack.donaciones.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final List<String> EXTENSIONES_PERMITIDAS = Arrays.asList(
            "jpg", "jpeg", "png", "webp", "gif"
    );

    private final Path rootLocation;

    public FileStorageService(@Value("${donatrack.upload.dir:uploads/donaciones}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.rootLocation);
            log.info("Directorio de almacenamiento de imágenes inicializado en: {}", this.rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de subida de archivos: " + this.rootLocation, e);
        }
    }

    /**
     * Guarda una imagen en el servidor validando extensión y previniendo ataques de Path Traversal.
     * Retorna el nombre único generado para el archivo.
     */
    public String guardarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No se puede almacenar un archivo vacío.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "imagen.jpg");

        // Seguridad: Control de Path Traversal
        if (originalFilename.contains("..")) {
            throw new SecurityException("Nombre de archivo inválido por contención de secuencias relativas: " + originalFilename);
        }

        // Validación de extensión
        String extension = extraerExtension(originalFilename).toLowerCase();
        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new IllegalArgumentException("Formato de imagen no permitido: " + extension + ". Formatos aceptados: " + EXTENSIONES_PERMITIDAS);
        }

        // Generar nombre determinístico y único
        String nombreUnico = UUID.randomUUID().toString() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");

        try {
            Path targetLocation = this.rootLocation.resolve(nombreUnico);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Archivo almacenado exitosamente: {} en {}", nombreUnico, targetLocation);
            return nombreUnico;
        } catch (IOException e) {
            log.error("Fallo al guardar el archivo {}: {}", nombreUnico, e.getMessage());
            throw new RuntimeException("Error al almacenar el archivo en disco.", e);
        }
    }

    /**
     * Carga el archivo desde el disco como Resource para su streaming HTTP al frontend.
     */
    public Resource cargarArchivoComoRecurso(String nombreArchivo) {
        if (nombreArchivo == null || nombreArchivo.contains("..")) {
            throw new SecurityException("Acceso denegado al archivo solicitado.");
        }

        try {
            Path filePath = this.rootLocation.resolve(nombreArchivo).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("El archivo solicitado no existe o no se puede leer: " + nombreArchivo);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Ruta de archivo malformada: " + nombreArchivo, e);
        }
    }

    /**
     * Determina el Content-Type adecuado para la respuesta HTTP de la imagen.
     */
    public String determinarContentType(String nombreArchivo) {
        String ext = extraerExtension(nombreArchivo).toLowerCase();
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/jpeg";
        };
    }

    private String extraerExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < filename.length() - 1) ? filename.substring(dotIndex + 1) : "";
    }
}
