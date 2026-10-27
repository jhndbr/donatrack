package com.donatrack.notificaciones.controller;

import com.donatrack.common.enums.CanalNotificacion;
import com.donatrack.notificaciones.domain.AuditoriaNotificacion;
import com.donatrack.notificaciones.domain.EstadoNotificacion;
import com.donatrack.notificaciones.repository.AuditoriaNotificacionRepository;
import com.donatrack.notificaciones.service.NotificacionesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificacionesController.class)
public class NotificacionesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditoriaNotificacionRepository auditoriaRepo;

    @MockBean
    private NotificacionesService notificacionesService;

    @Test
    void testObtenerHistorial() throws Exception {
        AuditoriaNotificacion a = new AuditoriaNotificacion(
                "clave-1", "lucia@mail.com", CanalNotificacion.EMAIL,
                "Asunto", "Mensaje", EstadoNotificacion.ENVIADO, null
        );
        when(auditoriaRepo.findTop50ByOrderByTimestampDesc()).thenReturn(List.of(a));

        mockMvc.perform(get("/api/v1/notificaciones/historial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].destinatario").value("lucia@mail.com"))
                .andExpect(jsonPath("$[0].estado").value("ENVIADO"));
    }

    @Test
    void testObtenerEstadisticas() throws Exception {
        when(auditoriaRepo.count()).thenReturn(10L);
        when(auditoriaRepo.countByEstado(EstadoNotificacion.ENVIADO)).thenReturn(8L);
        when(auditoriaRepo.countByEstado(EstadoNotificacion.FALLIDO)).thenReturn(1L);
        when(auditoriaRepo.countByEstado(EstadoNotificacion.DUPLICADO_IGNORADO)).thenReturn(1L);

        mockMvc.perform(get("/api/v1/notificaciones/estadisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistros").value(10))
                .andExpect(jsonPath("$.enviados").value(8))
                .andExpect(jsonPath("$.fallidos").value(1));
    }

    @Test
    void testEnvioManualTest() throws Exception {
        when(notificacionesService.despacharConAuditoria(anyString(), eq("TEST_MANUAL"), eq(CanalNotificacion.EMAIL), eq("test@mail.com"), anyString(), anyString()))
                .thenReturn(true);

        mockMvc.perform(post("/api/v1/notificaciones/test")
                        .param("canal", "EMAIL")
                        .param("destinatario", "test@mail.com")
                        .param("asunto", "Asunto Test")
                        .param("mensaje", "Cuerpo Test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.destinatario").value("test@mail.com"));
    }

    @Test
    void testConteoNoLeidasYMarcarComoLeida() throws Exception {
        when(auditoriaRepo.countByDestinatarioIgnoreCaseAndLeidaFalse("lucia@mail.com")).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notificaciones/no-leidas/count")
                        .param("destinatario", "lucia@mail.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noLeidas").value(3));

        AuditoriaNotificacion a = new AuditoriaNotificacion(
                "clave-1", "lucia@mail.com", CanalNotificacion.EMAIL,
                "Asunto", "Mensaje", EstadoNotificacion.ENVIADO, null
        );
        a.setId(7L);
        when(auditoriaRepo.findById(7L)).thenReturn(java.util.Optional.of(a));
        when(auditoriaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/notificaciones/7/leer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.leida").value(true));
    }
}
