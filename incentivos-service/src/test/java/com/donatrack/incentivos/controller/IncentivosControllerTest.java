package com.donatrack.incentivos.controller;

import com.donatrack.common.enums.CategoriaDonante;
import com.donatrack.incentivos.domain.Mision;
import com.donatrack.incentivos.domain.PerfilDonanteIncentivo;
import com.donatrack.incentivos.domain.RankingMensual;
import com.donatrack.incentivos.repository.MisionRepository;
import com.donatrack.incentivos.repository.PerfilDonanteIncentivoRepository;
import com.donatrack.incentivos.repository.ProgresoMisionRepository;
import com.donatrack.incentivos.repository.RankingMensualRepository;
import com.donatrack.incentivos.service.IncentivosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncentivosController.class)
public class IncentivosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncentivosService incentivosService;

    @MockBean
    private PerfilDonanteIncentivoRepository perfilRepository;

    @MockBean
    private ProgresoMisionRepository progresoRepository;

    @MockBean
    private RankingMensualRepository rankingRepository;

    @MockBean
    private MisionRepository misionRepository;

    @MockBean
    private com.donatrack.incentivos.repository.InsigniaRepository insigniaRepository;

    @MockBean
    private com.donatrack.incentivos.scheduler.IncentivosScheduler incentivosScheduler;

    @Test
    void testListarMisionesActivas() throws Exception {
        Mision m = new Mision("Mision 1", "Desc 1", Mision.TipoMision.RACHA, CategoriaDonante.COLABORADOR, 1.0, 1, "Insignia 1");
        when(misionRepository.findAll()).thenReturn(List.of(m));

        mockMvc.perform(get("/api/v1/incentivos/misiones/activas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Mision 1"));
    }

    @Test
    void testObtenerMetricasExistente() throws Exception {
        PerfilDonanteIncentivo perfil = new PerfilDonanteIncentivo(10L, "Donante 10", "d10@mail.com");
        when(perfilRepository.findByIdDonante(10L)).thenReturn(Optional.of(perfil));

        mockMvc.perform(get("/api/v1/incentivos/donantes/10/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDonante").value(10L))
                .andExpect(jsonPath("$.nombre").value("Donante 10"));
    }

    @Test
    void testObtenerMetricasInexistenteRetornaNotFound() throws Exception {
        when(perfilRepository.findByIdDonante(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/incentivos/donantes/999/metricas"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testObtenerRankingMensual() throws Exception {
        RankingMensual r = new RankingMensual(2026, 10, 1, 10L, "Donante 10", 5);
        when(rankingRepository.findByAnioAndMesOrderByPosicionAsc(anyInt(), anyInt())).thenReturn(List.of(r));

        mockMvc.perform(get("/api/v1/incentivos/rankings/mensual?anio=2026&mes=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].posicion").value(1))
                .andExpect(jsonPath("$[0].nombreDonante").value("Donante 10"));
    }

    @Test
    void testRegistrarActividad() throws Exception {
        String body = """
                {
                  "nombre": "Carlos",
                  "email": "carlos@mail.com",
                  "cantidadBienes": 12.0
                }
                """;

        mockMvc.perform(post("/api/v1/incentivos/donantes/5/actividad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void testObtenerAnaliticaDonante() throws Exception {
        when(incentivosService.obtenerAnaliticaDonante(10L)).thenReturn(java.util.Map.of(
                "idDonante", 10L,
                "nombre", "Donante 10",
                "categoria", "COLABORADOR",
                "misionesCompletadasTotales", 3
        ));

        mockMvc.perform(get("/api/v1/incentivos/donantes/10/analitica"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDonante").value(10L))
                .andExpect(jsonPath("$.nombre").value("Donante 10"))
                .andExpect(jsonPath("$.categoria").value("COLABORADOR"));
    }

    @Test
    void testForzarCalculoRanking() throws Exception {
        RankingMensual r = new RankingMensual(2026, 10, 1, 10L, "Donante 10", 12);
        when(incentivosScheduler.calcularRankingParaMes(2026, 10)).thenReturn(List.of(r));

        mockMvc.perform(post("/api/v1/incentivos/rankings/forzar-calculo?anio=2026&mes=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].posicion").value(1))
                .andExpect(jsonPath("$[0].misionesCompletadasMes").value(12));
    }
}
