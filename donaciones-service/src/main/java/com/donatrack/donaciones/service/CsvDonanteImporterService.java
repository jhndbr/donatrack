package com.donatrack.donaciones.service;

import com.donatrack.common.enums.CanalNotificacion;
import com.donatrack.donaciones.domain.*;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de Importación Masiva de Donantes por CSV (donaciones-service) - DonaTrack
 * Implementa ingesta en streaming (I/O bufferizado) con sincronización relacional por lotes (flush/clear)
 * cada 500 registros para soportar archivos de más de 20.000 filas con uso constante de memoria RAM (CU-12).
 * Genera un reporte detallado de discrepancias y errores por fila para el administrador.
 */
@Service
public class CsvDonanteImporterService {

    private final PersonaDonanteRepository donanteRepository;
    private final com.donatrack.donaciones.repository.OutboxEventRepository outboxEventRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int BATCH_SIZE = 500;

    public CsvDonanteImporterService(PersonaDonanteRepository donanteRepository) {
        this(donanteRepository, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CsvDonanteImporterService(PersonaDonanteRepository donanteRepository,
                                     @org.springframework.beans.factory.annotation.Autowired(required = false) com.donatrack.donaciones.repository.OutboxEventRepository outboxEventRepository,
                                     @org.springframework.beans.factory.annotation.Autowired(required = false) com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.donanteRepository = donanteRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public static class ImportSummary {
        private int totalProcesados = 0;
        private int creados = 0;
        private int actualizados = 0;
        private int errores = 0;
        private final List<FilaRechazada> erroresDetalle = new ArrayList<>();

        public int getTotalProcesados() { return totalProcesados; }
        public int getCreados() { return creados; }
        public int getActualizados() { return actualizados; }
        public int getErrores() { return errores; }
        public List<FilaRechazada> getErroresDetalle() { return erroresDetalle; }

        public void agregarError(int nroFila, String contenido, String motivo) {
            this.errores++;
            this.erroresDetalle.add(new FilaRechazada(nroFila, contenido, motivo));
        }
    }

    public static class FilaRechazada {
        private final int nroFila;
        private final String contenido;
        private final String motivoError;

        public FilaRechazada(int nroFila, String contenido, String motivoError) {
            this.nroFila = nroFila;
            this.contenido = contenido;
            this.motivoError = motivoError;
        }

        public int getNroFila() { return nroFila; }
        public String getContenido() { return contenido; }
        public String getMotivoError() { return motivoError; }
    }

    /**
     * Importacion eficiente mediante flujo (Streaming) y procesamiento por lotes (Batch).
     * Soporta archivos de mas de 20.000 filas con uso constante y minimo de memoria RAM.
     */
    @Transactional
    public ImportSummary importarDesdeStream(InputStream inputStream) {
        ImportSummary summary = new ImportSummary();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String linea;
            boolean primeraLinea = true;
            int countInBatch = 0;
            int nroFila = 0;

            while ((linea = reader.readLine()) != null) {
                nroFila++;
                if (primeraLinea) {
                    primeraLinea = false;
                    // Saltar encabezado si coincide con "TipoPersona"
                    if (linea.toLowerCase().contains("tipopersona")) {
                        continue;
                    }
                }

                if (linea.trim().isEmpty()) {
                    continue;
                }

                summary.totalProcesados++;
                try {
                    boolean ok = procesarLinea(nroFila, linea, summary);
                    if (ok) {
                        countInBatch++;
                    }

                    // Forzar sincronizacion de lote y liberar primer nivel de cache de Hibernate
                    if (countInBatch >= BATCH_SIZE) {
                        entityManager.flush();
                        entityManager.clear();
                        countInBatch = 0;
                    }
                } catch (Exception e) {
                    summary.agregarError(nroFila, linea, "Error procesando registro: " + e.getMessage());
                }
            }

            if (countInBatch > 0) {
                entityManager.flush();
                entityManager.clear();
            }

        } catch (Exception e) {
            throw new RuntimeException("Error durante el procesamiento del archivo CSV", e);
        }

        return summary;
    }

    private boolean procesarLinea(int nroFila, String linea, ImportSummary summary) {
        // Formato: TipoPersona,TipoDoc,Documento,Nombre/RazonSocial,Email,Telefono
        String[] partes = linea.split(",");
        if (partes.length < 5) {
            summary.agregarError(nroFila, linea, "Campos insuficientes (se esperan al menos 5 columnas: Tipo, DocTipo, Documento, Nombre, Email)");
            return false;
        }

        String tipoPersona = partes[0].trim().toUpperCase();
        String tipoDoc = partes[1].trim();
        String documento = partes[2].trim();
        String nombreORazonSocial = partes[3].trim();
        String email = partes[4].trim();
        String telefono = partes.length > 5 ? partes[5].trim() : "";

        if (email.isBlank() || !email.contains("@")) {
            summary.agregarError(nroFila, linea, "Formato de correo electrónico inválido: " + email);
            return false;
        }

        if (documento.isBlank()) {
            summary.agregarError(nroFila, linea, "El documento o CUIT no puede estar vacío");
            return false;
        }

        Optional<PersonaDonante> existenteOpt = donanteRepository.findByEmail(email);
        if (existenteOpt.isEmpty()) {
            if ("JURIDICA".equals(tipoPersona)) {
                existenteOpt = donanteRepository.findByCuit(documento).map(j -> (PersonaDonante) j);
            } else {
                existenteOpt = donanteRepository.findByNroDocumento(documento).map(h -> (PersonaDonante) h);
            }
        }

        if (existenteOpt.isPresent()) {
            PersonaDonante existente = existenteOpt.get();
            existente.registrarActividad();
            // Actualizar datos
            if (existente instanceof PersonaHumana humana) {
                String[] nombres = dividirNombreApellido(nombreORazonSocial);
                humana.setNombre(nombres[0]);
                humana.setApellido(nombres[1]);
                if (!tipoDoc.isEmpty()) {
                    humana.setTipoDocumento(tipoDoc);
                }
                humana.setNroDocumento(documento);
            } else if (existente instanceof PersonaJuridica juridica) {
                juridica.setRazonSocial(nombreORazonSocial);
                juridica.setCuit(documento);
            }
            summary.actualizados++;
            donanteRepository.save(existente);
        } else {
            PersonaDonante nuevo;
            if ("JURIDICA".equals(tipoPersona)) {
                PersonaJuridica jur = new PersonaJuridica(nombreORazonSocial, documento, "General", "General");
                jur.agregarMedioContacto(new MedioContacto(CanalNotificacion.EMAIL, email, true));
                if (!telefono.isEmpty()) {
                    jur.agregarMedioContacto(new MedioContacto(CanalNotificacion.WHATSAPP, telefono, false));
                }
                nuevo = jur;
            } else {
                String[] nombres = dividirNombreApellido(nombreORazonSocial);
                PersonaHumana nueva = new PersonaHumana(nombres[0], nombres[1], 30, documento, "No especificado", "Sin direccion");
                nueva.setTipoDocumento(tipoDoc.isEmpty() ? "DNI" : tipoDoc);
                nueva.agregarMedioContacto(new MedioContacto(CanalNotificacion.EMAIL, email, true));
                if (!telefono.isEmpty()) {
                    nueva.agregarMedioContacto(new MedioContacto(CanalNotificacion.WHATSAPP, telefono, false));
                }
                nuevo = nueva;
            }
            nuevo.setActivo(true);
            summary.creados++;
            PersonaDonante guardado = donanteRepository.save(nuevo);

            if (outboxEventRepository != null && objectMapper != null) {
                try {
                    com.donatrack.donaciones.domain.MedioContacto m = guardado.getMedioContactoPredeterminado();
                    com.donatrack.common.enums.CanalNotificacion canal = (m != null && m.getTipo() != null)
                            ? m.getTipo() : com.donatrack.common.enums.CanalNotificacion.EMAIL;
                    String contacto = (m != null && m.getValor() != null) ? m.getValor() : guardado.getEmailPrincipal();

                    com.donatrack.common.events.DonanteRegistradoEvent ev = new com.donatrack.common.events.DonanteRegistradoEvent(
                            guardado.getId(),
                            guardado.getNombreCompleto(),
                            guardado.getEmailPrincipal(),
                            canal,
                            contacto,
                            guardado.getEmailPrincipal(),
                            "donante123",
                            "IMPORTACION_CSV"
                    );
                    String payloadJson = objectMapper.writeValueAsString(ev);
                    outboxEventRepository.save(new com.donatrack.donaciones.domain.OutboxEvent(
                            "PersonaDonante",
                            guardado.getId() != null ? guardado.getId().toString() : "0",
                            "DonanteRegistradoEvent",
                            "donante.registrado",
                            payloadJson
                    ));
                } catch (Exception e) {
                    // Ignorar error outbox en batch CSV para no abortar el streaming
                }
            }
        }
        return true;
    }

    private String[] dividirNombreApellido(String texto) {
        String[] partes = texto.split(" ", 2);
        if (partes.length == 1) {
            return new String[]{partes[0], ""};
        }
        return partes;
    }
}
