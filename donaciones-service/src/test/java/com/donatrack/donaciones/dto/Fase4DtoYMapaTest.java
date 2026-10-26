package com.donatrack.donaciones.dto;

import com.donatrack.donaciones.service.CsvDonanteImporterService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("Pruebas Unitarias - Fase 4: DTOs, Bean Validation, Streaming CSV y Mapa Público")
public class Fase4DtoYMapaTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("BienInputDto debe validar campos obligatorios y cantidad estrictamente positiva")
    void testBienInputDtoValidation() {
        BienInputDto invalidBien = new BienInputDto("", "", "", -5.0, "", null, null, null);
        var violations = validator.validate(invalidBien);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("descripcion")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("categoria")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("subcategoria")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("cantidad")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("unidadMedida")));

        BienInputDto validBien = new BienInputDto("Fideos", "Alimentos", "Pastas", 10.0, "KG", false, LocalDate.now().plusMonths(6), "http://foto.jpg");
        var validViolations = validator.validate(validBien);
        assertTrue(validViolations.isEmpty());
    }

    @Test
    @DisplayName("CargaSegmentadaRequestDto debe exigir donanteId y lista no vacía de bienes")
    void testCargaSegmentadaRequestDtoValidation() {
        CargaSegmentadaRequestDto sinBienes = new CargaSegmentadaRequestDto(1L, Collections.emptyList());
        var violations = validator.validate(sinBienes);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("bienes")));

        CargaSegmentadaRequestDto valido = new CargaSegmentadaRequestDto(1L, List.of(
                new BienInputDto("Arroz", "Alimentos", "Granos", 5.0, "KG", false, null, null)
        ));
        assertTrue(validator.validate(valido).isEmpty());
    }

    @Test
    @DisplayName("CsvDonanteImporterService debe registrar el detalle de fila y motivo de discrepancia en ImportSummary (CU-12)")
    void testCsvDiscrepancyReport() {
        var donanteRepository = mock(com.donatrack.donaciones.repository.PersonaDonanteRepository.class);
        var importer = new CsvDonanteImporterService(donanteRepository);

        String csvData = """
                TipoPersona,TipoDoc,Documento,Nombre,Email,Telefono
                HUMANA,DNI,12345678,Juan Perez,correo_invalido_sin_arroba,123456
                HUMANA,DNI
                """;

        ByteArrayInputStream is = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));
        CsvDonanteImporterService.ImportSummary summary = importer.importarDesdeStream(is);

        assertEquals(2, summary.getErrores());
        assertEquals(2, summary.getErroresDetalle().size());

        var error1 = summary.getErroresDetalle().get(0);
        assertEquals(2, error1.getNroFila());
        assertTrue(error1.getMotivoError().contains("correo electrónico inválido"));

        var error2 = summary.getErroresDetalle().get(1);
        assertEquals(3, error2.getNroFila());
        assertTrue(error2.getMotivoError().contains("Campos insuficientes"));
    }

    @Test
    @DisplayName("DonacionMapaPublicoDto debe encapsular correctamente los datos comunitarios")
    void testDonacionMapaPublicoDto() {
        DonacionMapaPublicoDto dto = new DonacionMapaPublicoDto(
                100L,
                "Abrigo",
                15.0,
                "unidades",
                true,
                "Hogar Niños Felices",
                "Calle 123",
                -34.6037,
                -58.3816,
                java.time.LocalDateTime.now(),
                List.of("http://foto1.jpg", "http://foto2.jpg")
        );

        assertEquals(100L, dto.idDonacion());
        assertEquals("Abrigo", dto.subcategoria());
        assertEquals("Hogar Niños Felices", dto.entidadBeneficiaria());
        assertEquals(2, dto.fotosBienes().size());
    }
}
