package com.donatrack.donaciones.controller;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.Donacion;
import com.donatrack.donaciones.domain.PersonaHumana;
import com.donatrack.donaciones.matchmaking.MatchmakingRankingResult;
import com.donatrack.donaciones.repository.DonacionRepository;
import com.donatrack.donaciones.service.DonacionService;
import com.donatrack.donaciones.service.MatchmakingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DonacionController.class)
public class DonacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DonacionService donacionService;

    @MockBean
    private MatchmakingService matchmakingService;

    @MockBean
    private DonacionRepository donacionRepository;

    @MockBean
    private com.donatrack.donaciones.service.FileStorageService fileStorageService;

    @MockBean
    private com.donatrack.donaciones.scheduler.DonacionesScheduler donacionesScheduler;

    @Test
    void testEjecutarMatchmakingBatchManual() throws Exception {
        when(donacionesScheduler.procesarMatchmakingDonaciones()).thenReturn(3);

        mockMvc.perform(post("/api/v1/donaciones/matchmaking/ejecutar-batch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.donacionesAsignadas").value(3));
    }

    @Test
    void testSaludoEntrega1() throws Exception {
        mockMvc.perform(get("/api/v1/donaciones/saludo"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hola desde el servicio de Donaciones."));
    }

    @Test
    void testListarDonaciones() throws Exception {
        PersonaHumana d = new PersonaHumana("Juan", "Perez", 30, "111", "M", "Dir 1");
        Donacion don = new Donacion(d, "Alimentos", 10.0, "kg", false, null);
        don.setId(1L);

        when(donacionRepository.findAll()).thenReturn(List.of(don));

        mockMvc.perform(get("/api/v1/donaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].subcategoria").value("Alimentos"));
    }

    @Test
    void testObtenerPorIdExistente() throws Exception {
        PersonaHumana d = new PersonaHumana("Juan", "Perez", 30, "111", "M", "Dir 1");
        Donacion don = new Donacion(d, "Alimentos", 10.0, "kg", false, null);
        don.setId(1L);

        when(donacionRepository.findById(1L)).thenReturn(Optional.of(don));

        mockMvc.perform(get("/api/v1/donaciones/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void testObtenerPorIdInexistenteRetornaNotFound() throws Exception {
        when(donacionRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/donaciones/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCargaSegmentada() throws Exception {
        PersonaHumana d = new PersonaHumana("Juan", "Perez", 30, "111", "M", "Dir 1");
        Donacion don = new Donacion(d, "Lacteos", 20.0, "litros", false, null);
        don.setId(5L);

        when(donacionService.registrarCargaSegmentada(eq(1L), any())).thenReturn(List.of(don));

        String body = """
                [
                  {"nombre": "Leche", "subcategoria": "Lacteos", "cantidad": 20.0, "unidadMedida": "litros"}
                ]
                """;

        mockMvc.perform(post("/api/v1/donaciones/carga-segmentada")
                        .param("donanteId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5L))
                .andExpect(jsonPath("$[0].subcategoria").value("Lacteos"));
    }

    @Test
    void testEjecutarMatchmaking() throws Exception {
        MatchmakingRankingResult result = new MatchmakingRankingResult(1L, "Alimentos",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        when(matchmakingService.evaluarDonacion(1L)).thenReturn(result);

        mockMvc.perform(get("/api/v1/donaciones/1/matchmaking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDonacion").value(1L))
                .andExpect(jsonPath("$.subcategoria").value("Alimentos"));
    }

    @Test
    void testAsignarDonacion() throws Exception {
        PersonaHumana d = new PersonaHumana("Juan", "Perez", 30, "111", "M", "Dir 1");
        Donacion don = new Donacion(d, "Alimentos", 10.0, "kg", false, null);
        don.setId(1L);
        don.setEstado(EstadoDonacion.ASIGNACION_REALIZADA);

        when(donacionService.asignarDonacion(1L, 2L)).thenReturn(don);

        mockMvc.perform(post("/api/v1/donaciones/1/asignar")
                        .param("entidadId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.estado").value("ASIGNACION_REALIZADA"));
    }

    @Test
    void testActualizarEstado() throws Exception {
        PersonaHumana d = new PersonaHumana("Juan", "Perez", 30, "111", "M", "Dir 1");
        Donacion don = new Donacion(d, "Alimentos", 10.0, "kg", false, null);
        don.setId(1L);
        don.setEstado(EstadoDonacion.LISTA_PARA_ENTREGAR);

        when(donacionService.actualizarEstado(eq(1L), eq(EstadoDonacion.LISTA_PARA_ENTREGAR), any())).thenReturn(don);

        String body = """
                {"nuevoEstado": "LISTA_PARA_ENTREGAR"}
                """;

        mockMvc.perform(patch("/api/v1/donaciones/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTA_PARA_ENTREGAR"));
    }

    @Test
    void testSubirImagen() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "archivo", "foto.jpg", "image/jpeg", "dummy-content".getBytes()
        );
        when(fileStorageService.guardarArchivo(any())).thenReturn("uuid_foto.jpg");

        mockMvc.perform(multipart("/api/v1/donaciones/subir-imagen").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/api/v1/donaciones/imagenes/uuid_foto.jpg"))
                .andExpect(jsonPath("$.nombreArchivo").value("uuid_foto.jpg"));
    }

    @Test
    void testServirImagen() throws Exception {
        org.springframework.core.io.ByteArrayResource res = new org.springframework.core.io.ByteArrayResource("image-bytes".getBytes());
        when(fileStorageService.cargarArchivoComoRecurso("uuid_foto.jpg")).thenReturn(res);
        when(fileStorageService.determinarContentType("uuid_foto.jpg")).thenReturn("image/jpeg");

        mockMvc.perform(get("/api/v1/donaciones/imagenes/uuid_foto.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }
}
