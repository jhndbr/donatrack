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
import java.util.Optional;

@Service
public class CsvDonanteImporterService {

    private final PersonaDonanteRepository donanteRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int BATCH_SIZE = 500;

    public CsvDonanteImporterService(PersonaDonanteRepository donanteRepository) {
        this.donanteRepository = donanteRepository;
    }

    public static class ImportSummary {
        private int totalProcesados = 0;
        private int creados = 0;
        private int actualizados = 0;
        private int errores = 0;

        public int getTotalProcesados() { return totalProcesados; }
        public int getCreados() { return creados; }
        public int getActualizados() { return actualizados; }
        public int getErrores() { return errores; }
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

            while ((linea = reader.readLine()) != null) {
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

                try {
                    procesarLinea(linea, summary);
                    summary.totalProcesados++;
                    countInBatch++;

                    // Forzar sincronizacion de lote y liberar primer nivel de cache de Hibernate
                    if (countInBatch >= BATCH_SIZE) {
                        entityManager.flush();
                        entityManager.clear();
                        countInBatch = 0;
                    }
                } catch (Exception e) {
                    summary.errores++;
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

    private void procesarLinea(String linea, ImportSummary summary) {
        // Formato: TipoPersona,TipoDoc,Documento,Nombre/RazonSocial,Email,Telefono
        String[] partes = linea.split(",");
        if (partes.length < 5) {
            summary.errores++;
            return;
        }

        String tipoPersona = partes[0].trim().toUpperCase();
        String tipoDoc = partes[1].trim();
        String documento = partes[2].trim();
        String nombreORazonSocial = partes[3].trim();
        String email = partes[4].trim();
        String telefono = partes.length > 5 ? partes[5].trim() : "";

        Optional<PersonaDonante> existenteOpt = donanteRepository.findByEmail(email);

        if (existenteOpt.isPresent()) {
            PersonaDonante existente = existenteOpt.get();
            existente.registrarActividad();
            // Actualizar datos
            if (existente instanceof PersonaHumana humana) {
                String[] nombres = dividirNombreApellido(nombreORazonSocial);
                humana.setNombre(nombres[0]);
                humana.setApellido(nombres[1]);
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
                PersonaHumana hum = new PersonaHumana(nombres[0], nombres[1], 30, documento, "No especificado", "Sin direccion");
                hum.agregarMedioContacto(new MedioContacto(CanalNotificacion.EMAIL, email, true));
                if (!telefono.isEmpty()) {
                    hum.agregarMedioContacto(new MedioContacto(CanalNotificacion.WHATSAPP, telefono, false));
                }
                nuevo = hum;
            }
            summary.creados++;
            donanteRepository.save(nuevo);
        }
    }

    private String[] dividirNombreApellido(String texto) {
        String[] partes = texto.split(" ", 2);
        if (partes.length == 1) {
            return new String[]{partes[0], ""};
        }
        return partes;
    }
}
