package com.donatrack.frontend;

import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiClientService apiClientService;

    @Test
    @DisplayName("GET / debe renderizar la landing page principal")
    void testLandingPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("GET /comprobantes debe renderizar la página de comprobantes institucionales")
    void testComprobantesPage() throws Exception {
        mockMvc.perform(get("/comprobantes"))
                .andExpect(status().isOk())
                .andExpect(view().name("comprobantes"));
    }

    @Test
    @DisplayName("GET /privacidad debe renderizar los términos y políticas")
    void testPrivacidadPage() throws Exception {
        mockMvc.perform(get("/privacidad"))
                .andExpect(status().isOk())
                .andExpect(view().name("privacidad"));
    }

    @Test
    @DisplayName("GET /admin sin sesión debe redirigir a /login por seguridad")
    void testAdminUnauthenticatedRedirect() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=true&redirect=/admin"));
    }

    @Test
    @DisplayName("GET /donante sin sesión debe redirigir a /login")
    void testDonanteUnauthenticatedRedirect() throws Exception {
        mockMvc.perform(get("/donante"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=true&redirect=/donante"));
    }

    @Test
    @DisplayName("GET /beneficiaria sin sesión debe redirigir a /login")
    void testBeneficiariaUnauthenticatedRedirect() throws Exception {
        mockMvc.perform(get("/beneficiaria"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=true&redirect=/beneficiaria"));
    }

    @Test
    @DisplayName("GET /admin con rol no autorizado (ROLE_DONANTE) debe redirigir con denied=true")
    void testAdminRoleMismatchRedirect() throws Exception {
        UserSession donanteSession = new UserSession("donante1", "Juan Donante", "juan@test.com", "ROLE_DONANTE");
        mockMvc.perform(get("/admin").sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, donanteSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?denied=true"));
    }

    @Test
    @DisplayName("GET /donante con sesión válida debe renderizar el portal del donante")
    void testDonanteDashboard() throws Exception {
        UserSession session = new UserSession("donante1", "Juan Donante", "juan@test.com", "ROLE_DONANTE");
        session.setDonanteId(1L);

        when(apiClientService.listarDonaciones(any())).thenReturn(List.of(
                Map.of("id", 1L, "estado", "EN_DEPOSITO", "bienes", List.of(), "categoria", "Alimentos")
        ));
        when(apiClientService.obtenerMetricasDonante(any())).thenReturn(
                Map.of("puntosTotales", 100, "misionesCompletadas", 2, "categoria", "ORO", "totalDonaciones", 5, "totalBienesDonados", 20)
        );
        when(apiClientService.listarInsigniasDonante(any())).thenReturn(List.of());

        mockMvc.perform(get("/donante").sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().isOk())
                .andExpect(view().name("donante/dashboard"))
                .andExpect(model().attributeExists("donaciones"))
                .andExpect(model().attributeExists("metricas"));
    }

    @Test
    @DisplayName("GET /beneficiaria con sesión válida debe renderizar el portal de entidad beneficiaria")
    void testBeneficiariaDashboard() throws Exception {
        UserSession session = new UserSession("comedor1", "Comedor Esperanza", "comedor@test.com", "ROLE_BENEFICIARIA");
        session.setEntidadId(1L);

        when(apiClientService.listarEntidades()).thenReturn(List.of(
                Map.of("id", 1L, "nombre", "Comedor Los Bajitos")
        ));
        when(apiClientService.listarDonaciones(any())).thenReturn(List.of(
                Map.of("id", 1L, "subcategoria", "LACTEOS", "estado", "LISTA_PARA_ENTREGAR", "cantidad", 50.0, "unidadMedida", "LITROS")
        ));

        mockMvc.perform(get("/beneficiaria").sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().isOk())
                .andExpect(view().name("beneficiaria/dashboard"))
                .andExpect(model().attributeExists("entidades"))
                .andExpect(model().attributeExists("donacionesAsignadas"));
    }

    @Test
    @DisplayName("GET /admin con sesión válida debe renderizar el panel administrativo")
    void testAdminDashboard() throws Exception {
        UserSession session = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.listarDonaciones(any())).thenReturn(List.of());
        when(apiClientService.listarCamiones()).thenReturn(List.of());
        when(apiClientService.listarEntidades()).thenReturn(List.of());
        when(apiClientService.listarDonantes()).thenReturn(List.of());

        mockMvc.perform(get("/admin").sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("totalDonaciones"))
                .andExpect(model().attributeExists("totalCamiones"));
    }

    @Test
    @DisplayName("GET /admin/rankings con sesión válida debe renderizar la vista de tabla de posiciones")
    void testAdminRankings() throws Exception {
        UserSession session = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.obtenerRankingMensual(any(), any())).thenReturn(List.of(
                Map.of("idDonante", 1L, "nombreDonante", "Juan Pérez", "puntosMes", 150, "misionesCompletadasMes", 4, "posicion", 1)
        ));

        mockMvc.perform(get("/admin/rankings").sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/rankings"))
                .andExpect(model().attributeExists("rankings"));
    }

    @Test
    @DisplayName("POST /login con credenciales inválidas debe fallar y redirigir con error")
    void testLoginInvalidCredentials() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "clave_incorrecta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=bad_credentials"));
    }

    @Test
    @DisplayName("POST /login con admin/admin123 debe autenticar y redirigir a /admin")
    void testLoginAdminSuccess() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(result -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(
                            result.getRequest().getSession().getAttribute(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY)
                    );
                });
    }

    @Test
    @DisplayName("GET /logout debe invalidar sesión y redirigir a /login")
    void testLogout() throws Exception {
        UserSession session = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");
        mockMvc.perform(get("/logout").sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /donante/nueva-donacion con sesión válida debe renderizar formulario")
    void testDonanteNuevaDonacionForm() throws Exception {
        UserSession session = new UserSession("donante1", "Juan Donante", "juan@test.com", "ROLE_DONANTE");
        session.setDonanteId(1L);

        when(apiClientService.listarEntidades()).thenReturn(List.of(
                Map.of("id", 1L, "razonSocial", "Comedor Esperanza")
        ));

        mockMvc.perform(get("/donante/nueva-donacion")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().isOk())
                .andExpect(view().name("donante/nueva_donacion"))
                .andExpect(model().attributeExists("entidades"));
    }

    @Test
    @DisplayName("POST /donante/donar debe crear donación y redirigir a /donante/donaciones")
    void testDonanteDonarPost() throws Exception {
        UserSession session = new UserSession("donante1", "Juan Donante", "juan@test.com", "ROLE_DONANTE");
        session.setDonanteId(1L);

        when(apiClientService.crearDonacionSegmentada(any(), any())).thenReturn(true);

        mockMvc.perform(post("/donante/donar")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("categoria", "Alimentos")
                        .param("subcategoria", "arroz")
                        .param("cantidad", "25.0")
                        .param("unidadMedida", "kg")
                        .param("condicion", "NUEVO")
                        .param("descripcion", "25 kg de arroz de prueba"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donante/donaciones"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /beneficiaria/confirmar debe confirmar entrega y redirigir a /beneficiaria")
    void testBeneficiariaConfirmarRecepcionPost() throws Exception {
        UserSession session = new UserSession("comedor1", "Comedor Esperanza", "comedor@test.com", "ROLE_BENEFICIARIA");
        session.setEntidadId(1L);

        when(apiClientService.entregarDonacion(any(), any())).thenReturn(true);

        mockMvc.perform(post("/beneficiaria/confirmar")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("donacionId", "1")
                        .param("fotoUrl", "https://img.test/foto.jpg")
                        .param("observaciones", "Recibido todo en orden"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /beneficiaria/necesidades debe registrar necesidad y redirigir a /beneficiaria")
    void testBeneficiariaRegistrarNecesidadPost() throws Exception {
        UserSession session = new UserSession("comedor1", "Comedor Esperanza", "comedor@test.com", "ROLE_BENEFICIARIA");
        session.setEntidadId(1L);

        when(apiClientService.crearNecesidad(any(), any())).thenReturn(true);

        mockMvc.perform(post("/beneficiaria/necesidades")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("subcategoria", "leche")
                        .param("descripcion", "Leche para niños")
                        .param("tipo", "EXTRAORDINARIA")
                        .param("cantidadSolicitada", "50.0")
                        .param("unidadMedida", "litros"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }
}

