package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.domain.Ruta;
import com.donatrack.logistica.dto.PlanificadorCallbackDto;
import com.donatrack.logistica.repository.EntregaRepository;
import com.donatrack.logistica.repository.RutaRepository;
import com.donatrack.logistica.service.LogisticaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RutaController.class)
public class RutaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RutaRepository rutaRepository;

    @MockBean
    private EntregaRepository entregaRepository;

    @MockBean
    private LogisticaService logisticaService;

    @Test
    void testListarRutas() throws Exception {
        Ruta ruta = new Ruta("CAMION-01", "AE789XY", LocalDate.now());
        ruta.setId("RUTA-1");

        when(rutaRepository.findAll()).thenReturn(List.of(ruta));

        mockMvc.perform(get("/api/v1/logistica/rutas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("RUTA-1"))
                .andExpect(jsonPath("$[0].patenteCamion").value("AE789XY"));
    }

    @Test
    void testObtenerRutaExistente() throws Exception {
        Ruta ruta = new Ruta("CAMION-01", "AE789XY", LocalDate.now());
        ruta.setId("RUTA-1");

        when(rutaRepository.findById("RUTA-1")).thenReturn(Optional.of(ruta));

        mockMvc.perform(get("/api/v1/logistica/rutas/RUTA-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("RUTA-1"));
    }

    @Test
    void testObtenerRutaInexistenteRetornaNotFound() throws Exception {
        when(rutaRepository.findById("RUTA-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/logistica/rutas/RUTA-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPlanificarLote() throws Exception {
        PlanificadorCallbackDto dto = new PlanificadorCallbackDto();
        dto.setLoteId("LOTE-TEST-01");

        when(logisticaService.planificarLotes(anyList(), any())).thenReturn(List.of(dto));

        String body = """
                {
                  "donacionIds": [1, 2, 3],
                  "idCamion": "CAMION-01"
                }
                """;

        mockMvc.perform(post("/api/v1/logistica/rutas/planificar-lote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loteId").value("LOTE-TEST-01"));
    }

    @Test
    void testIniciarRuta() throws Exception {
        Ruta ruta = new Ruta("CAMION-01", "AE789XY", LocalDate.now());
        ruta.setId("RUTA-1");
        ruta.iniciarRuta();

        when(logisticaService.iniciarRuta("RUTA-1")).thenReturn(ruta);

        mockMvc.perform(post("/api/v1/logistica/rutas/RUTA-1/iniciar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("RUTA-1"))
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));
    }

    @Test
    void testConfirmarEntrega() throws Exception {
        Entrega entrega = new Entrega("RUTA-1", 101L, 1L, "Comedor", "donante@mail.com", null, "entidad@mail.com", "AE789XY");
        entrega.setId("ENT-1");
        entrega.confirmarRecepcion(List.of("http://foto.jpg"));

        when(logisticaService.confirmarEntrega(eq("ENT-1"), anyList())).thenReturn(entrega);

        String body = """
                {"fotos": ["http://foto.jpg"]}
                """;

        mockMvc.perform(post("/api/v1/logistica/rutas/entregas/ENT-1/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ENT-1"))
                .andExpect(jsonPath("$.estado").value("ENTREGADA"));
    }
}
