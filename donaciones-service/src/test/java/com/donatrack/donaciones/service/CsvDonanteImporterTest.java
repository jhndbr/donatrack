package com.donatrack.donaciones.service;

import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CsvDonanteImporterTest {

    @Mock
    private PersonaDonanteRepository donanteRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CsvDonanteImporterService importerService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(importerService, "entityManager", entityManager);
    }

    @Test
    void testImportarCsvDonantesNuevosYExistentes() {
        String csvData = """
                TipoPersona,TipoDoc,Documento,Nombre/Razon Social,Email,Telefono
                HUMANA,DNI,12345678,Ana Perez,ana@mail.com,+54 11 5555-5555
                JURIDICA,CUIT,30-12345678-9,Arcos Plateados S.A.,contacto@empresa.com,+54 11 4444-4444
                """;

        when(donanteRepository.findByEmail("ana@mail.com")).thenReturn(Optional.empty());
        PersonaHumana existente = new PersonaHumana("Arcos", "S.A.", 40, "30-12345678-9", "Empresa", "Calle 1");
        when(donanteRepository.findByEmail("contacto@empresa.com")).thenReturn(Optional.of(existente));

        ByteArrayInputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        CsvDonanteImporterService.ImportSummary summary = importerService.importarDesdeStream(is);

        assertEquals(2, summary.getTotalProcesados());
        assertEquals(1, summary.getCreados());
        assertEquals(1, summary.getActualizados());
        assertEquals(0, summary.getErrores());
        verify(donanteRepository, times(2)).save(any());
    }

    @Test
    void testIgnorarLineasVaciasYContarErrores() {
        String csvData = """
                TipoPersona,TipoDoc,Documento,Nombre/Razon Social,Email,Telefono
                HUMANA,DNI
                
                HUMANA,DNI,99999999,Carlos Ruiz,carlos@mail.com,+54 11 1111-2222
                """;

        when(donanteRepository.findByEmail("carlos@mail.com")).thenReturn(Optional.empty());

        ByteArrayInputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        CsvDonanteImporterService.ImportSummary summary = importerService.importarDesdeStream(is);

        assertEquals(2, summary.getTotalProcesados());
        assertEquals(1, summary.getCreados());
        assertEquals(1, summary.getErrores()); // La linea 'HUMANA,DNI' tiene campos insuficientes
    }
}
