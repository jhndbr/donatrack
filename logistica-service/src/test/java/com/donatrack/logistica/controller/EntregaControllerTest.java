package com.donatrack.logistica.controller;

import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.dto.ComprobanteEntregaDto;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.EntregaFallidaRequestDto;
import com.donatrack.logistica.dto.ReplanificarRequestDto;
import com.donatrack.logistica.exception.ConflictException;
import com.donatrack.logistica.exception.GlobalExceptionHandler;
import com.donatrack.logistica.exception.ResourceNotFoundException;
import com.donatrack.logistica.service.EntregaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EntregaController.class)
@Import(GlobalExceptionHandler.class)
public class EntregaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EntregaService entregaService;

    private Entrega entrega;

    @BeforeEach
    void setUp() {
        entrega = new Entrega("RUTA-01", 100L, 10L, "Comedor Los Bajitos", "comedor@mail.com", 42L, "donante@mail.com", "AE789XY");
        entrega.setId("ENT-100");
    }

    @Test
    void testListarTodas() throws Exception {
        when(entregaService.listarTodas()).thenReturn(List.of(entrega));

        mockMvc.perform(get("/api/v1/logistica/entregas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ENT-100"))
                .andExpect(jsonPath("$[0].patenteCamion").value("AE789XY"));
    }

    @Test
    void testObtenerPorIdExistente() throws Exception {
        when(entregaService.obtenerPorId("ENT-100")).thenReturn(entrega);

        mockMvc.perform(get("/api/v1/logistica/entregas/ENT-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ENT-100"))
                .andExpect(jsonPath("$.nombreEntidad").value("Comedor Los Bajitos"));
    }

    @Test
    void testObtenerPorIdInexistenteRetornaNotFound() throws Exception {
        when(entregaService.obtenerPorId("ENT-999")).thenThrow(new ResourceNotFoundException("Entrega no encontrada: ENT-999"));

        mockMvc.perform(get("/api/v1/logistica/entregas/ENT-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Entrega no encontrada: ENT-999"));
    }

    @Test
    void testConfirmarEntregaExitoso() throws Exception {
        entrega.confirmarRecepcion(List.of("http://foto.jpg"), "Carlos", "12345", null);
        when(entregaService.confirmarEntrega(eq("ENT-100"), any(ConfirmarEntregaRequestDto.class))).thenReturn(entrega);

        String json = """
                {
                    "fotos": ["http://foto.jpg"],
                    "receptorNombre": "Carlos",
                    "receptorDni": "12345"
                }
                """;

        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENTREGADA"))
                .andExpect(jsonPath("$.receptorNombre").value("Carlos"));
    }

    @Test
    void testConfirmarEntregaSinFotosFallaValidacion() throws Exception {
        String json = """
                {
                    "fotos": [],
                    "receptorNombre": "Carlos"
                }
                """;

        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testConfirmarEntregaEntidadNoAutorizadaRetornaConflict() throws Exception {
        when(entregaService.confirmarEntrega(eq("ENT-100"), any(ConfirmarEntregaRequestDto.class)))
                .thenThrow(new ConflictException("Entidad no autorizada para confirmar entrega ajena"));

        String json = """
                {
                    "fotos": ["http://foto.jpg"],
                    "idEntidadBeneficiaria": 999
                }
                """;

        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Entidad no autorizada para confirmar entrega ajena"));
    }

    @Test
    void testRegistrarEntregaFallidaOpcionesFallidaYNoRecibida() throws Exception {
        entrega.registrarNoRecibida("Cerrado", true);
        when(entregaService.registrarEntregaFallida(eq("ENT-100"), any(EntregaFallidaRequestDto.class))).thenReturn(entrega);

        String json = """
                {
                    "motivo": "Comedor cerrado en horario de reparto",
                    "replanificable": true
                }
                """;

        // Prueba endpoint /fallida
        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/fallida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_RECIBIDA"));

        // Prueba endpoint alias /no-recibida (exigido por spec de auditoría)
        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/no-recibida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_RECIBIDA"));
    }

    @Test
    void testRegistrarEntregaFallidaSinMotivoFallaValidacion() throws Exception {
        String json = """
                {
                    "motivo": "",
                    "replanificable": true
                }
                """;

        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/fallida")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRegistrarArriboSede() throws Exception {
        entrega.registrarArriboSede("Llegada a portón");
        when(entregaService.registrarArriboSede(eq("ENT-100"), any())).thenReturn(entrega);

        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/arribo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observaciones\": \"Camión en puerta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaOperativa").value("EN_PUERTA_SEDE"));
    }

    @Test
    void testAdjuntarEvidencias() throws Exception {
        entrega.agregarEvidencias(List.of("http://remito-firmado.jpg"));
        when(entregaService.agregarEvidencias(eq("ENT-100"), anyList())).thenReturn(entrega);

        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/evidencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fotos\": [\"http://remito-firmado.jpg\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void testReplanificarEntregaExitoso() throws Exception {
        entrega.registrarNoRecibida("Falla previa", true);
        entrega.replanificar("Reingreso");
        when(entregaService.replanificarEntrega(eq("ENT-100"), any(ReplanificarRequestDto.class))).thenReturn(entrega);

        String json = """
                {
                    "regresoAlDeposito": true,
                    "motivo": "Reingresado físicamente"
                }
                """;

        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/replanificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.etapaOperativa").value("PENDIENTE_RETIRO_DEPOSITO"));
    }

    @Test
    void testReplanificarEntregaSinRegresoADepositoRetornaConflict() throws Exception {
        when(entregaService.replanificarEntrega(eq("ENT-100"), any(ReplanificarRequestDto.class)))
                .thenThrow(new IllegalStateException("La entrega solo puede replanificarse si la donación regresó físicamente al depósito en buen estado (CU-45)"));

        String json = """
                {
                    "regresoAlDeposito": false,
                    "motivo": "Mercadería extraviada"
                }
                """;

        mockMvc.perform(post("/api/v1/logistica/entregas/ENT-100/replanificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void testObtenerComprobante() throws Exception {
        ComprobanteEntregaDto dto = new ComprobanteEntregaDto(
                "ENT-100", 100L, 10L, "Comedor Los Bajitos",
                "AE789XY", "Carlos Receptor", "12345",
                "http://firma.png", List.of("http://foto.png"),
                LocalDateTime.now(), "ENTREGADA"
        );
        when(entregaService.obtenerComprobante("ENT-100")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/logistica/entregas/ENT-100/comprobante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEntrega").value("ENT-100"))
                .andExpect(jsonPath("$.receptorNombre").value("Carlos Receptor"))
                .andExpect(jsonPath("$.estado").value("ENTREGADA"));
    }
}