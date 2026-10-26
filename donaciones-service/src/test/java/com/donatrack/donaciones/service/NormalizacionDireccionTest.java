package com.donatrack.donaciones.service;

import com.donatrack.donaciones.domain.EntidadBeneficiaria;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.dto.DireccionNormalizadaDto;
import com.donatrack.donaciones.exception.DireccionInvalidaException;
import com.donatrack.donaciones.repository.EntidadBeneficiariaRepository;
import com.donatrack.donaciones.repository.PersonaDonanteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - Normalización y Validación Geográfica de Direcciones (Georef AR)")
public class NormalizacionDireccionTest {

    private NormalizacionDireccionService normalizacionService;

    @Mock
    private EntidadBeneficiariaRepository entidadRepository;

    @Mock
    private PersonaDonanteRepository donanteRepository;

    @BeforeEach
    void setUp() {
        // Inicializamos con el servicio de Georef AR (timeout 1.5s, modo estricto con fallback offline determinístico)
        normalizacionService = new NormalizacionDireccionService(
                "https://apis.datos.gob.ar/georef/api/direcciones",
                1500,
                true
        );
    }

    @Test
    @DisplayName("Fallback offline determinístico cuando Georef AR no está disponible o sin conexión")
    void testFallbackOfflineSinConexion() {
        NormalizacionDireccionService offlineService = new NormalizacionDireccionService(
                "http://127.0.0.1:59999/georef/inexistente",
                200,
                true
        );
        DireccionNormalizadaDto validaOffline = offlineService.evaluarDireccion("Av. Corrientes 1234, CABA");
        assertTrue(validaOffline.esValida());
        assertEquals(1234, validaOffline.altura());
        assertNotNull(validaOffline.latitud());
        assertNotNull(validaOffline.longitud());

        DireccionNormalizadaDto invalidaOffline = offlineService.evaluarDireccion("Calle Unicornio Magico 999999");
        assertFalse(invalidaOffline.esValida());
    }

    @Test
    @DisplayName("Dirección real argentina debe normalizarse con nomenclatura oficial y coordenadas GPS")
    void testNormalizarDireccionRealValida() {
        DireccionNormalizadaDto resultado = normalizacionService.evaluarDireccion("Av. Corrientes 1234, CABA");

        assertTrue(resultado.esValida(), "Debe ser considerada válida");
        assertNotNull(resultado.nomenclatura(), "Debe tener nomenclatura oficial");
        assertTrue(resultado.nomenclatura().toUpperCase().contains("CORRIENTES"), "Debe contener el nombre de la calle");
        assertNotNull(resultado.latitud(), "Debe tener latitud");
        assertNotNull(resultado.longitud(), "Debe tener longitud");
        assertEquals(1234, resultado.altura(), "La altura catastral debe ser 1234");
    }

    @Test
    @DisplayName("Dirección sin altura catastral (solo nombre de calle) debe ser rechazada")
    void testRechazarDireccionSinAltura() {
        DireccionNormalizadaDto resultado = normalizacionService.evaluarDireccion("Corrientes");

        assertFalse(resultado.esValida(), "Una calle sin número no es válida para logística");
        assertTrue(resultado.mensajeValidacion().contains("altura") || resultado.mensajeValidacion().contains("número"),
                "Debe indicar que falta la altura catastral");

        assertThrows(DireccionInvalidaException.class, () -> normalizacionService.normalizar("Corrientes"));
    }

    @Test
    @DisplayName("Dirección ficticia o inexistente debe ser rechazada con excepción")
    void testRechazarDireccionInexistente() {
        DireccionNormalizadaDto resultado = normalizacionService.evaluarDireccion("Calle Unicornio Magico 999999");

        assertFalse(resultado.esValida(), "Una calle inexistente debe ser rechazada");
        assertThrows(DireccionInvalidaException.class, () ->
                normalizacionService.normalizar("Calle Unicornio Magico 999999")
        );
    }

    @Test
    @DisplayName("Dirección nula o vacía debe ser rechazada de inmediato")
    void testRechazarDireccionVacia() {
        DireccionNormalizadaDto resultado = normalizacionService.evaluarDireccion("   ");

        assertFalse(resultado.esValida());
        assertThrows(DireccionInvalidaException.class, () -> normalizacionService.normalizar(""));
        assertThrows(DireccionInvalidaException.class, () -> normalizacionService.normalizar(null));
    }

    @Test
    @DisplayName("EntidadBeneficiariaService debe guardar la dirección normalizada y coordenadas al registrar")
    void testRegistrarEntidadConDireccionNormalizada() {
        NormalizacionDireccionService mockNormalizacion = mock(NormalizacionDireccionService.class);
        when(mockNormalizacion.normalizar("Av. Medrano 951"))
                .thenReturn(DireccionNormalizadaDto.valida(
                        "Av. Medrano 951",
                        "AV MEDRANO 951, Comuna 5, Ciudad Autónoma de Buenos Aires",
                        "AV MEDRANO",
                        951,
                        "Comuna 5",
                        "Ciudad Autónoma de Buenos Aires",
                        "Ciudad Autónoma de Buenos Aires",
                        -34.5985,
                        -58.4202
                ));

        when(entidadRepository.save(any(EntidadBeneficiaria.class))).thenAnswer(inv -> inv.getArgument(0));

        EntidadBeneficiariaService service = new EntidadBeneficiariaService(
                entidadRepository,
                mock(com.donatrack.donaciones.repository.NecesidadMaterialRepository.class),
                mock(com.donatrack.donaciones.repository.DonacionRepository.class),
                mockNormalizacion
        );

        EntidadBeneficiaria entidad = new EntidadBeneficiaria("Comedor Test", "Av. Medrano 951", "11223344");
        EntidadBeneficiaria guardada = service.registrarEntidad(entidad);

        assertEquals("AV MEDRANO 951, Comuna 5, Ciudad Autónoma de Buenos Aires", guardada.getDireccion());
        assertEquals(-34.5985, guardada.getLatitud());
        assertEquals(-58.4202, guardada.getLongitud());
        verify(mockNormalizacion, times(1)).normalizar("Av. Medrano 951");
    }

    @Test
    @DisplayName("DonanteService debe guardar la dirección normalizada y coordenadas en donante humano")
    void testRegistrarDonanteConDireccionNormalizada() {
        NormalizacionDireccionService mockNormalizacion = mock(NormalizacionDireccionService.class);
        when(mockNormalizacion.normalizar("Gorriti 3800"))
                .thenReturn(DireccionNormalizadaDto.valida(
                        "Gorriti 3800",
                        "GORRITI 3800, Comuna 14, Ciudad Autónoma de Buenos Aires",
                        "GORRITI",
                        3800,
                        "Comuna 14",
                        "Ciudad Autónoma de Buenos Aires",
                        "Ciudad Autónoma de Buenos Aires",
                        -34.5954,
                        -58.4185
                ));

        when(donanteRepository.save(any(PersonaHumana.class))).thenAnswer(inv -> inv.getArgument(0));

        DonanteService service = new DonanteService(
                donanteRepository,
                mock(com.donatrack.donaciones.repository.DonacionRepository.class),
                mockNormalizacion
        );

        PersonaHumana donante = new PersonaHumana("Carlos", "Gómez", 35, "35111222", "M", "Gorriti 3800");
        donante.agregarMedioContacto(new com.donatrack.donaciones.domain.MedioContacto(
                com.donatrack.common.enums.CanalNotificacion.EMAIL, "carlos@test.com", true
        ));

        PersonaHumana guardado = service.registrarDonanteHumano(donante);

        assertEquals("GORRITI 3800, Comuna 14, Ciudad Autónoma de Buenos Aires", guardado.getDireccion());
        assertEquals(-34.5954, guardado.getLatitud());
        assertEquals(-58.4185, guardado.getLongitud());
        verify(mockNormalizacion, times(1)).normalizar("Gorriti 3800");
    }
}
