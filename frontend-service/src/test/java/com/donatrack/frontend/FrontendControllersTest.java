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

import static org.mockito.ArgumentMatchers.*;
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
    @DisplayName("POST /donante/donar con entidadId debe crear donación asignada directamente")
    void testDonanteDonarPostConEntidadEspecifica() throws Exception {
        UserSession session = new UserSession("donante1", "Juan Donante", "juan@test.com", "ROLE_DONANTE");
        session.setDonanteId(1L);

        when(apiClientService.crearDonacionSegmentada(any(), any(), org.mockito.ArgumentMatchers.eq(5L))).thenReturn(true);

        mockMvc.perform(post("/donante/donar")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("entidadId", "5")
                        .param("categoria", "Alimentos")
                        .param("subcategoria", "fideos")
                        .param("cantidad", "10.0")
                        .param("unidadMedida", "kg")
                        .param("condicion", "NUEVO")
                        .param("descripcion", "10 kg de fideos para comedor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donante/donaciones"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /beneficiaria/confirmar debe confirmar entrega y redirigir a /beneficiaria")
    void testBeneficiariaConfirmarRecepcionPost() throws Exception {
        UserSession session = new UserSession("comedor1", "Comedor Esperanza", "comedor@test.com", "ROLE_BENEFICIARIA");
        session.setEntidadId(1L);

        when(apiClientService.confirmarEntregaPorDonacion(eq(1L), any(), any(), any(), any(), any())).thenReturn(true);
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

    @Test
    @DisplayName("POST /admin/donaciones/nueva debe registrar donación segmentada con descripcion y redirigir a /admin")
    void testAdminGuardarDonacionSegmentadaPost() throws Exception {
        UserSession session = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.crearDonacionSegmentada(any(), any())).thenReturn(true);

        mockMvc.perform(post("/admin/donaciones/nueva")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("donanteId", "1")
                        .param("nombreBien", "Fideos guiseros")
                        .param("subcategoria", "fideos")
                        .param("cantidad", "40.0")
                        .param("unidadMedida", "kg")
                        .param("esUsado", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("POST /registro/donante debe invocar creación en ApiClient y redirigir a /login")
    void testRegistroDonantePublico() throws Exception {
        when(apiClientService.crearDonanteHumana(any())).thenReturn(Map.of("id", 10L, "nombre", "Carlos"));

        mockMvc.perform(post("/registro/donante")
                        .param("nombre", "Carlos")
                        .param("apellido", "Gómez")
                        .param("dni", "35123456")
                        .param("edad", "30")
                        .param("email", "carlos@test.com")
                        .param("direccion", "Av. Rivadavia 1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /registro/entidad debe invocar creación en ApiClient y redirigir a /login")
    void testRegistroEntidadPublico() throws Exception {
        when(apiClientService.crearEntidad(any())).thenReturn(Map.of("id", 20L, "razonSocial", "Hogar Niños"));

        mockMvc.perform(post("/registro/entidad")
                        .param("razonSocial", "Hogar Niños")
                        .param("direccion", "Belgrano 500")
                        .param("telefono", "11-2345-6789")
                        .param("email", "hogar@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("GET /donante/donaciones/{id}/historial retorna el historial de estados de la donación")
    void testHistorialDonacionEndpoint() throws Exception {
        UserSession session = new UserSession("donante1", "Juan Donante", "juan@test.com", "ROLE_DONANTE");
        session.setDonanteId(1L);

        when(apiClientService.obtenerHistorialDonacion(1L)).thenReturn(List.of(
                Map.of("estadoNuevo", "EN_DEPOSITO", "motivo", "Ingreso", "usuario", "OPERADOR")
        ));

        mockMvc.perform(get("/donante/donaciones/1/historial")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoNuevo").value("EN_DEPOSITO"));
    }

    @Test
    @DisplayName("POST /donante/insignias/{id}/visibilidad actualiza la visibilidad de la insignia (CU-52)")
    void testAlternarVisibilidadInsignia() throws Exception {
        UserSession session = new UserSession("donante1", "Juan Donante", "juan@test.com", "ROLE_DONANTE");
        session.setDonanteId(1L);

        when(apiClientService.alternarVisibilidadInsignia(10L, true)).thenReturn(true);

        mockMvc.perform(post("/donante/insignias/10/visibilidad")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("visible", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/donante/incentivos"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /beneficiaria/fallida registra incidencia de entrega no recibida (CU-44)")
    void testRegistrarEntregaFallida() throws Exception {
        UserSession session = new UserSession("comedor1", "Comedor Esperanza", "comedor@test.com", "ROLE_BENEFICIARIA");
        session.setEntidadId(1L);

        when(apiClientService.registrarEntregaFallida("ENT-01", "Camión no llegó", true)).thenReturn(true);

        mockMvc.perform(post("/beneficiaria/fallida")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("idEntrega", "ENT-01")
                        .param("motivo", "Camión no llegó")
                        .param("replanificable", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /admin/donantes/{id}/baja aplica baja lógica a donante (CU-11)")
    void testBajaLogicaDonante() throws Exception {
        UserSession session = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.darDeBajaDonante(5L, "Solicitud formal")).thenReturn(true);

        mockMvc.perform(post("/admin/donantes/5/baja")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("motivo", "Solicitud formal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /admin/entidades/{id}/baja aplica baja lógica a entidad (CU-25)")
    void testBajaLogicaEntidad() throws Exception {
        UserSession session = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.darDeBajaEntidad(8L, "Cierre de sede")).thenReturn(true);

        mockMvc.perform(post("/admin/entidades/8/baja")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("motivo", "Cierre de sede"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("GET /api/v1/donaciones/imagenes/{archivo} sirve imagen proxy sin requerir autenticación")
    void testServirImagenDonacionProxy() throws Exception {
        byte[] dummyBytes = new byte[]{1, 2, 3, 4};
        when(apiClientService.obtenerImagenDonacion("prueba.png"))
                .thenReturn(org.springframework.http.ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                        .body(dummyBytes));

        mockMvc.perform(get("/api/v1/donaciones/imagenes/prueba.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(dummyBytes));
    }

    @Test
    @DisplayName("GET /api/v1/donaciones/imagenes/{archivo} inexistente devuelve fallback SVG 200 OK")
    void testServirImagenDonacionFallbackSvg() throws Exception {
        when(apiClientService.obtenerImagenDonacion("inexistente.jpg"))
                .thenReturn(org.springframework.http.ResponseEntity.notFound().build());

        mockMvc.perform(get("/api/v1/donaciones/imagenes/inexistente.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("image/svg+xml")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<svg")));
    }

    @Test
    @DisplayName("GET /uploads/{archivo} no requiere sesión y sirve recurso o fallback SVG")
    void testServirUploadsSinSesion() throws Exception {
        mockMvc.perform(get("/uploads/test_foto.jpg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("image/")));
    }

    @Test
    @DisplayName("GET /images/placeholder-entrega.svg devuelve el vector SVG con status 200")
    void testPlaceholderEntregaDirecto() throws Exception {
        mockMvc.perform(get("/images/placeholder-entrega.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("image/svg+xml")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<svg")));
    }

    @Test
    @DisplayName("POST /admin/matchmaking/ejecutar dispara el batch de asignación de donaciones")
    void testEjecutarMatchmakingBatchAdmin() throws Exception {
        UserSession session = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.ejecutarMatchmakingBatch()).thenReturn(Map.of("donacionesAsignadas", 2));

        mockMvc.perform(post("/admin/matchmaking/ejecutar")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("GET /api/direcciones/validar retorna evaluación geográfica en JSON")
    void testValidarDireccionEndpoint() throws Exception {
        when(apiClientService.normalizarDireccion("Av. Corrientes 1234"))
                .thenReturn(Map.of(
                        "esValida", true,
                        "nomenclatura", "AV CORRIENTES 1234, CABA",
                        "latitud", -34.6037,
                        "longitud", -58.3816
                ));

        mockMvc.perform(get("/api/direcciones/validar").param("direccion", "Av. Corrientes 1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.esValida").value(true))
                .andExpect(jsonPath("$.nomenclatura").value("AV CORRIENTES 1234, CABA"))
                .andExpect(jsonPath("$.latitud").value(-34.6037));
    }

    @Test
    @DisplayName("POST /registro/donante con dirección inválida debe rechazar con mensaje de error")
    void testRegistroDonanteDireccionInvalida() throws Exception {
        when(apiClientService.normalizarDireccion("Calle Fantasma 9999"))
                .thenReturn(Map.of("esValida", false, "mensajeValidacion", "La dirección no existe"));

        mockMvc.perform(post("/registro/donante")
                        .param("nombre", "Juan")
                        .param("apellido", "Pérez")
                        .param("dni", "35123456")
                        .param("email", "juan@test.com")
                        .param("direccion", "Calle Fantasma 9999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/registro"))
                .andExpect(flash().attributeExists("mensajeError"));
    }

    @Test
    @DisplayName("POST /registro/entidad con dirección inválida debe rechazar y solicitar dirección real")
    void testRegistroEntidadDireccionInvalida() throws Exception {
        when(apiClientService.normalizarDireccion("Calle Invalida Sin Altura"))
                .thenReturn(Map.of("esValida", false, "mensajeValidacion", "Falta número de puerta"));

        mockMvc.perform(post("/registro/entidad")
                        .param("razonSocial", "Comedor Solidario")
                        .param("direccion", "Calle Invalida Sin Altura")
                        .param("telefono", "11223344")
                        .param("email", "comedor@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/registro"))
                .andExpect(flash().attributeExists("mensajeError"));
    }

    @Test
    @DisplayName("GET /donante/notificaciones renderiza el centro de avisos del donante")
    void testDonanteNotificaciones() throws Exception {
        UserSession session = new UserSession("donante1", "Juan Donante", "juan@test.com", "ROLE_DONANTE");
        session.setDonanteId(1L);

        when(apiClientService.obtenerDonante(1L)).thenReturn(Map.of("id", 1L, "emailPrincipal", "juan@test.com"));
        when(apiClientService.listarNotificaciones("juan@test.com")).thenReturn(List.of(
                Map.of("id", 1, "canal", "EMAIL", "destinatario", "juan@test.com", "asunto", "Donación Asignada", "estado", "ENVIADO", "claveEvento", "notif-donante-asignacion-1")
        ));

        mockMvc.perform(get("/donante/notificaciones")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().isOk())
                .andExpect(view().name("donante/notificaciones"))
                .andExpect(model().attributeExists("notificaciones", "emailDonante"));
    }

    @Test
    @DisplayName("GET /admin/notificaciones y POST /admin/notificaciones/test gestionan auditoría y envíos de prueba")
    void testAdminNotificaciones() throws Exception {
        UserSession session = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.obtenerEstadisticasNotificaciones()).thenReturn(Map.of(
                "totalRegistros", 5,
                "enviados", 5,
                "fallidos", 0,
                "duplicadosIgnorados", 5
        ));
        when(apiClientService.listarNotificaciones(null)).thenReturn(List.of(
                Map.of("id", 1, "canal", "EMAIL", "destinatario", "juan@test.com", "asunto", "Donación Asignada", "estado", "ENVIADO", "claveEvento", "notif-1")
        ));
        when(apiClientService.enviarNotificacionPrueba("EMAIL", "test@donatrack.org", "Asunto Test", "Mensaje Test")).thenReturn(true);

        mockMvc.perform(get("/admin/notificaciones")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/notificaciones"))
                .andExpect(model().attributeExists("estadisticas", "notificaciones"));

        mockMvc.perform(post("/admin/notificaciones/test")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, session)
                        .param("canal", "EMAIL")
                        .param("destinatario", "test@donatrack.org")
                        .param("asunto", "Asunto Test")
                        .param("mensaje", "Mensaje Test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notificaciones"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("GET /buzon sin sesión debe redirigir a /login por seguridad y privacidad")
    void testBuzonUnauthenticatedRedirect() throws Exception {
        mockMvc.perform(get("/buzon"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=true&redirect=/buzon"));
    }

    @Test
    @DisplayName("GET /buzon con sesión admin renderiza la bandeja de auditoría de Mailpit")
    void testBuzonMailpit() throws Exception {
        UserSession adminSession = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.listarMensajesMailpit(null, 50)).thenReturn(Map.of(
                "total", 1,
                "unread", 1,
                "messages", List.of(
                        Map.of(
                                "ID", "msg-123",
                                "Subject", "Donación Asignada",
                                "Created", "2026-10-19T10:30:00Z",
                                "From", Map.of("Name", "DonaTrack", "Address", "notificaciones@donatrack.org"),
                                "To", List.of(Map.of("Address", "carlos@donatrack.org")),
                                "Snippet", "Tu donación ha sido asignada"
                        )
                )
        ));
        when(apiClientService.obtenerMensajeMailpit("msg-123")).thenReturn(Map.of(
                "ID", "msg-123",
                "Subject", "Donación Asignada",
                "HTML", "<h1>Donación Asignada</h1>",
                "From", Map.of("Name", "DonaTrack", "Address", "notificaciones@donatrack.org"),
                "To", List.of(Map.of("Address", "carlos@donatrack.org"))
        ));

        mockMvc.perform(get("/buzon")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("buzon"))
                .andExpect(model().attributeExists("messages", "total", "mensajeSeleccionado"));

        mockMvc.perform(get("/buzon/raw/msg-123")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, adminSession))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Donación Asignada")));
    }

    @Test
    @DisplayName("GET /buzon con sesión donante debe aislar mensajes con to:email")
    void testBuzonFiltradoPorDonanteLogueado() throws Exception {
        UserSession donanteSession = new UserSession("donante_juan", "Juan Pérez", "juan.donante@example.com", "ROLE_DONANTE");
        donanteSession.setDonanteId(25L);

        when(apiClientService.listarMensajesMailpit("to:juan.donante@example.com", 50)).thenReturn(Map.of(
                "total", 1,
                "unread", 0,
                "messages", List.of(
                        Map.of(
                                "ID", "msg-juan",
                                "Subject", "Bienvenido Juan",
                                "From", Map.of("Name", "DonaTrack", "Address", "notif@donatrack.org"),
                                "To", List.of(Map.of("Address", "juan.donante@example.com")),
                                "Snippet", "Tu donación ha sido registrada"
                        )
                )
        ));
        when(apiClientService.obtenerMensajeMailpit("msg-juan")).thenReturn(Map.of(
                "ID", "msg-juan",
                "Subject", "Bienvenido Juan",
                "HTML", "<p>Hola Juan</p>"
        ));

        mockMvc.perform(get("/buzon")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, donanteSession))
                .andExpect(status().isOk())
                .andExpect(view().name("buzon"))
                .andExpect(model().attribute("filtradoPorUsuario", true))
                .andExpect(model().attribute("emailUsuario", "juan.donante@example.com"));
    }

    @Test
    @DisplayName("GET /buzon?verTodos=true con sesión donante ignora verTodos y mantiene aislamiento obligatorio")
    void testBuzonVerTodosPorDonante() throws Exception {
        UserSession donanteSession = new UserSession("donante_juan", "Juan Pérez", "juan.donante@example.com", "ROLE_DONANTE");
        donanteSession.setDonanteId(25L);

        when(apiClientService.listarMensajesMailpit("to:juan.donante@example.com", 50)).thenReturn(Map.of(
                "total", 1,
                "unread", 0,
                "messages", List.of()
        ));

        mockMvc.perform(get("/buzon?verTodos=true")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, donanteSession))
                .andExpect(status().isOk())
                .andExpect(view().name("buzon"))
                .andExpect(model().attribute("filtradoPorUsuario", true))
                .andExpect(model().attribute("verTodos", false));
    }

    @Test
    @DisplayName("GET /buzon?verTodos=true con sesión administrador permite ver todos los correos")
    void testBuzonVerTodosAdmin() throws Exception {
        UserSession adminSession = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.listarMensajesMailpit(null, 50)).thenReturn(Map.of(
                "total", 10,
                "unread", 2,
                "messages", List.of()
        ));

        mockMvc.perform(get("/buzon?verTodos=true")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("buzon"))
                .andExpect(model().attribute("filtradoPorUsuario", false))
                .andExpect(model().attribute("verTodos", true));
    }

    @Test
    @DisplayName("GET /donante/notificaciones solo consulta notificaciones del email del donante y nunca devuelve todas")
    void testDonanteNotificacionesAislado() throws Exception {
        UserSession donanteSession = new UserSession("donante_juan", "Juan Pérez", "juan.donante@example.com", "ROLE_DONANTE");
        donanteSession.setDonanteId(25L);

        when(apiClientService.obtenerDonante(25L)).thenReturn(Map.of(
                "id", 25L,
                "nombreCompleto", "Juan Pérez",
                "emailPrincipal", "juan.donante@example.com"
        ));
        when(apiClientService.listarNotificaciones("juan.donante@example.com")).thenReturn(List.of(
                Map.of("id", 101, "canal", "EMAIL", "destinatario", "juan.donante@example.com", "asunto", "Notif Juan", "estado", "ENVIADO")
        ));

        mockMvc.perform(get("/donante/notificaciones")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, donanteSession))
                .andExpect(status().isOk())
                .andExpect(view().name("donante/notificaciones"))
                .andExpect(model().attribute("emailDonante", "juan.donante@example.com"))
                .andExpect(model().attribute("notificaciones", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("POST /registro/juridica registra donante corporativo exitosamente")
    void testRegistroDonanteJuridica() throws Exception {
        when(apiClientService.crearDonanteJuridica(any())).thenReturn(Map.of("id", 88L, "razonSocial", "Empresa S.A."));

        mockMvc.perform(post("/registro/juridica")
                        .param("razonSocial", "Empresa S.A.")
                        .param("cuit", "30-99887766-5")
                        .param("tipo", "Empresa")
                        .param("rubro", "Alimentos")
                        .param("email", "rse@empresa.com")
                        .param("password", "empresa123")
                        .param("telefono", "11-4000-0000")
                        .param("direccion", "Av. San Juan 1000, CABA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /admin/donaciones/{id}/marcar-vencida invoca actualización y redirige a /admin")
    void testMarcarDonacionVencida() throws Exception {
        UserSession adminSession = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");
        when(apiClientService.actualizarEstadoDonacion(10L, "VENCIDA", "Caducidad")).thenReturn(true);

        mockMvc.perform(post("/admin/donaciones/10/marcar-vencida")
                        .param("motivo", "Caducidad")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /admin/camiones/{id}/baja da de baja unidad y redirige")
    void testDarDeBajaCamion() throws Exception {
        UserSession adminSession = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");
        when(apiClientService.darDeBajaCamion("CAM-01", "Falla de motor")).thenReturn(true);

        mockMvc.perform(post("/admin/camiones/CAM-01/baja")
                        .param("motivo", "Falla de motor")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/camiones"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("GET /chofer con sesión ROLE_CHOFER muestra la hoja de ruta y paradas")
    void testPanelChofer() throws Exception {
        UserSession choferSession = new UserSession("chofer", "Chofer Flota", "chofer@donatrack.org", "ROLE_CHOFER");

        when(apiClientService.listarRutas()).thenReturn(List.of(
                Map.of("id", "RUTA-1", "patenteCamion", "AE789XY", "estado", "EN_CURSO")
        ));
        when(apiClientService.listarCamiones()).thenReturn(List.of(
                Map.of("id", "CAM-1", "patente", "AE789XY")
        ));
        when(apiClientService.listarEntregasPorRuta("RUTA-1")).thenReturn(List.of(
                Map.of("id", "ENT-1", "nombreEntidad", "Comedor Esperanza", "estado", "PENDIENTE", "idDonacion", 1L)
        ));

        mockMvc.perform(get("/chofer")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().isOk())
                .andExpect(view().name("chofer/ruta"))
                .andExpect(model().attributeExists("rutas"))
                .andExpect(model().attributeExists("rutaActiva"));
    }

    @Test
    @DisplayName("POST /chofer/rutas/{id}/iniciar inicia recorrido logístico")
    void testIniciarRutaChofer() throws Exception {
        UserSession choferSession = new UserSession("chofer", "Chofer Flota", "chofer@donatrack.org", "ROLE_CHOFER");
        when(apiClientService.iniciarRuta("RUTA-1")).thenReturn(true);

        mockMvc.perform(post("/chofer/rutas/RUTA-1/iniciar")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer?rutaId=RUTA-1"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /chofer/telemetria transmite coordenadas GPS")
    void testTelemetriaChofer() throws Exception {
        UserSession choferSession = new UserSession("chofer", "Chofer Flota", "chofer@donatrack.org", "ROLE_CHOFER");
        when(apiClientService.reportarTelemetria("CAM-01", "AE789XY", -34.6037, -58.3816, 50.0)).thenReturn(true);

        mockMvc.perform(post("/chofer/telemetria")
                        .param("idCamion", "CAM-01")
                        .param("patente", "AE789XY")
                        .param("latitud", "-34.6037")
                        .param("longitud", "-58.3816")
                        .param("velocidad", "50.0")
                        .param("rutaId", "RUTA-1")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer?rutaId=RUTA-1"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /chofer/entregas/{id}/confirmar registra recepción física con fotografía")
    void testConfirmarEntregaChofer() throws Exception {
        UserSession choferSession = new UserSession("chofer", "Chofer Flota", "chofer@donatrack.org", "ROLE_CHOFER");
        when(apiClientService.confirmarEntrega(any(), any())).thenReturn(true);

        mockMvc.perform(post("/chofer/entregas/ENT-1/confirmar")
                        .param("fotoUrl", "https://ejemplo.com/foto.jpg")
                        .param("rutaId", "RUTA-1")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer?rutaId=RUTA-1"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /login bloquea Open Redirect hacia dominios externos y redirige al panel de rol")
    void testLoginBloqueaOpenRedirect() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin123")
                        .param("redirect", "https://evil.com/phishing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("POST /beneficiaria/confirmar bloquea IDOR cuando la donación pertenece a otra entidad")
    void testBeneficiariaConfirmarBloqueaIdor() throws Exception {
        UserSession benefSession = new UserSession("comedor_esperanza", "Comedor Esperanza", "esperanza@comedor.org", "ROLE_BENEFICIARIA");
        benefSession.setEntidadId(1L);

        when(apiClientService.obtenerDonacion(99L)).thenReturn(Map.of(
                "id", 99L,
                "entidadAsignada", Map.of("id", 2L, "nombre", "Otra Entidad")
        ));

        mockMvc.perform(post("/beneficiaria/confirmar")
                        .param("donacionId", "99")
                        .param("comentario", "Intento IDOR")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, benefSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria"))
                .andExpect(flash().attributeExists("mensajeError"));
    }

    @Test
    @DisplayName("GET /donante/donaciones/{id}/historial bloquea IDOR con 403 cuando la donación pertenece a otro donante")
    void testDonanteHistorialBloqueaIdor() throws Exception {
        UserSession donanteSession = new UserSession("donante_juan", "Juan Pérez", "juan.donante@example.com", "ROLE_DONANTE");
        donanteSession.setDonanteId(1L);

        when(apiClientService.obtenerDonacion(50L)).thenReturn(Map.of(
                "id", 50L,
                "donante", Map.of("id", 99L, "nombreCompleto", "Otro Donante")
        ));

        mockMvc.perform(get("/donante/donaciones/50/historial")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, donanteSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /donante/donaciones?categoria=Alimentos filtra por categoría y expone categoriaSeleccionada en el modelo")
    void testDonanteDonacionesFiltroPorCategoria() throws Exception {
        UserSession donanteSession = new UserSession("donante_juan", "Juan Pérez", "juan.donante@example.com", "ROLE_DONANTE");
        donanteSession.setDonanteId(1L);

        when(apiClientService.listarDonantes()).thenReturn(List.of(Map.of("id", 1L, "nombreCompleto", "Juan Pérez")));
        when(apiClientService.obtenerDonante(1L)).thenReturn(Map.of("id", 1L, "nombreCompleto", "Juan Pérez"));
        Map<String, Object> donacionMock = new java.util.HashMap<>();
        donacionMock.put("id", 10L);
        donacionMock.put("estado", "EN_DEPOSITO");
        donacionMock.put("categoria", "Alimentos");
        donacionMock.put("subcategoria", "Arroz");
        donacionMock.put("cantidad", 10);
        donacionMock.put("unidadMedida", "KG");
        donacionMock.put("fechaRecepcion", "2026-10-15T10:00:00");
        when(apiClientService.listarDonacionesPaginadas(0, 8, null, 1L, null, "Alimentos")).thenReturn(Map.of(
                "content", List.of(donacionMock),
                "totalPages", 1,
                "totalElements", 1
        ));

        mockMvc.perform(get("/donante/donaciones")
                        .param("categoria", "Alimentos")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, donanteSession))
                .andExpect(status().isOk())
                .andExpect(view().name("donante/donaciones"))
                .andExpect(model().attribute("categoriaSeleccionada", "Alimentos"));
    }

    @Test
    @DisplayName("POST /admin/donantes/registrar registra donante humano o jurídico desde el panel admin")
    void testAdminRegistrarDonante() throws Exception {
        UserSession adminSession = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");
        when(apiClientService.crearDonanteHumana(any())).thenReturn(Map.of("id", 77L, "nombreCompleto", "Ana Gómez"));

        mockMvc.perform(post("/admin/donantes/registrar")
                        .param("tipoDonante", "HUMANA")
                        .param("nombre", "Ana")
                        .param("apellido", "Gómez")
                        .param("dni", "40111222")
                        .param("email", "ana@ejemplo.com")
                        .param("telefono", "11-5555-4444")
                        .param("direccion", "Av. Rivadavia 2000")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/donaciones/nueva"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("POST /beneficiaria/necesidades/{id}/editar y /eliminar y POST /admin/camiones/{id}/actualizar funcionan correctamente")
    void testCrudNecesidadesYActualizarCamion() throws Exception {
        UserSession benefSession = new UserSession("comedor_esperanza", "Comedor Esperanza", "esperanza@comedor.org", "ROLE_BENEFICIARIA");
        benefSession.setEntidadId(1L);
        UserSession adminSession = new UserSession("admin", "Administrador Central", "admin@donatrack.org", "ROLE_ADMIN");

        when(apiClientService.actualizarNecesidad(any(), any())).thenReturn(true);
        when(apiClientService.eliminarNecesidad(10L)).thenReturn(true);
        when(apiClientService.actualizarCamion(any(), any())).thenReturn(true);

        mockMvc.perform(post("/beneficiaria/necesidades/10/editar")
                        .param("entidadId", "1")
                        .param("descripcion", "Leche en polvo")
                        .param("cantidadRequerida", "20")
                        .param("urgencia", "ALTA")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, benefSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria/necesidades"))
                .andExpect(flash().attributeExists("mensajeExito"));

        mockMvc.perform(post("/beneficiaria/necesidades/10/eliminar")
                        .param("entidadId", "1")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, benefSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria/necesidades"))
                .andExpect(flash().attributeExists("mensajeExito"));

        mockMvc.perform(post("/admin/camiones/CAM-01/actualizar")
                        .param("patente", "AE123BC")
                        .param("marca", "Mercedes-Benz")
                        .param("modelo", "Atego")
                        .param("capacidadMaxKg", "8000")
                        .param("capacidadMaxVolumenM3", "35")
                        .param("estado", "DISPONIBLE")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/camiones"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("Flujo Chofer: Arribo en puerta, Confirmar con fotos, Fallida y Comprobante JSON")
    void testFlujoCompletoChoferLlegadaYConfirmacion() throws Exception {
        UserSession choferSession = new UserSession("chofer1", "Carlos Chofer", "chofer@donatrack.org", "ROLE_CHOFER");

        when(apiClientService.registrarArriboSedePorDonacion(eq(100L), any())).thenReturn(true);
        when(apiClientService.confirmarEntregaPorDonacion(eq(100L), any(), any(), any(), any())).thenReturn(true);
        when(apiClientService.registrarEntregaFallidaPorDonacion(eq(100L), any(), anyBoolean())).thenReturn(true);
        when(apiClientService.obtenerComprobanteEntregaPorDonacion(100L)).thenReturn(Map.of(
                "idDonacion", 100L,
                "estado", "ENTREGADA",
                "nombreEntidad", "Comedor San José",
                "patenteCamion", "AE789XY",
                "receptorNombre", "Laura Gómez"
        ));
        when(apiClientService.finalizarRuta("RUTA-1")).thenReturn(true);

        // 1. Arribo en puerta
        mockMvc.perform(post("/chofer/entregas/100/arribo")
                        .param("rutaId", "RUTA-1")
                        .param("observaciones", "En rampa de descarga")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer?rutaId=RUTA-1"))
                .andExpect(flash().attributeExists("mensajeExito"));

        // 2. Confirmación acta conjunta chofer + receptor
        mockMvc.perform(post("/chofer/entregas/100/confirmar")
                        .param("rutaId", "RUTA-1")
                        .param("receptorNombre", "Laura Gómez")
                        .param("receptorDni", "30111222")
                        .param("fotoUrl", "https://img.test/entrega.jpg")
                        .param("observaciones", "Recepción conforme")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer?rutaId=RUTA-1"))
                .andExpect(flash().attributeExists("mensajeExito"));

        // 3. Consulta de comprobante digital JSON
        mockMvc.perform(get("/chofer/comprobante/donacion/100")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDonacion").value(100))
                .andExpect(jsonPath("$.estado").value("ENTREGADA"))
                .andExpect(jsonPath("$.receptorNombre").value("Laura Gómez"));

        // 4. Incidencia / Falla
        mockMvc.perform(post("/chofer/entregas/100/fallida")
                        .param("rutaId", "RUTA-1")
                        .param("motivo", "Sede cerrada")
                        .param("etapaFalla", "EN_PUERTA_SEDE")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer?rutaId=RUTA-1"))
                .andExpect(flash().attributeExists("mensajeExito"));

        // 5. Finalizar ruta
        mockMvc.perform(post("/chofer/rutas/RUTA-1/finalizar")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, choferSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/chofer?rutaId=RUTA-1"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    @DisplayName("Flujo Entidad Beneficiaria: Alerta En Puerta en Dashboard, Confirmar con fotos, Fallida y Comprobante JSON")
    void testFlujoCompletoBeneficiariaRecepcionYComprobante() throws Exception {
        UserSession benefSession = new UserSession("comedor_esperanza", "Comedor Esperanza", "esperanza@comedor.org", "ROLE_BENEFICIARIA");
        benefSession.setEntidadId(5L);

        when(apiClientService.listarEntidades()).thenReturn(List.of(
                Map.of("id", 5L, "razonSocial", "Comedor Esperanza")
        ));
        when(apiClientService.listarDonaciones(any())).thenReturn(List.of(
                Map.of("id", 200L, "subcategoria", "Fideos", "cantidad", 30, "unidadMedida", "KG", "estado", "EN_TRASLADO",
                        "entidadAsignada", Map.of("id", 5L))
        ));
        when(apiClientService.listarRutas()).thenReturn(List.of(
                Map.of("id", "RUTA-05", "paradas", List.of(
                        Map.of("idDonacion", 200L, "estadoParada", "EN_PUERTA_SEDE")
                ))
        ));

        when(apiClientService.confirmarEntregaPorDonacion(eq(200L), any(), any(), any(), any(), eq(5L))).thenReturn(true);
        when(apiClientService.registrarEntregaFallidaPorDonacion(eq(200L), any(), anyBoolean())).thenReturn(true);
        when(apiClientService.obtenerComprobanteEntregaPorDonacion(200L)).thenReturn(Map.of(
                "idDonacion", 200L,
                "estado", "ENTREGADA",
                "nombreEntidad", "Comedor Esperanza",
                "patenteCamion", "AE789XY",
                "receptorNombre", "Marta López"
        ));

        // 1. Dashboard con aviso destacado de donación EN_PUERTA_SEDE
        mockMvc.perform(get("/beneficiaria").sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, benefSession))
                .andExpect(status().isOk())
                .andExpect(view().name("beneficiaria/dashboard"))
                .andExpect(model().attributeExists("donacionesEnPuerta"))
                .andExpect(model().attributeExists("estadoParadaPorDonacion"));

        // 2. Confirmación formal con fotos por parte del comedor
        mockMvc.perform(post("/beneficiaria/confirmar")
                        .param("entidadId", "5")
                        .param("donacionId", "200")
                        .param("receptorNombre", "Marta López")
                        .param("receptorDni", "28333444")
                        .param("fotoUrl", "https://img.test/recibido.jpg")
                        .param("observaciones", "Insumos en excelente estado")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, benefSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria?entidadId=5"))
                .andExpect(flash().attributeExists("mensajeExito"));

        // 3. Consulta de comprobante digital JSON
        mockMvc.perform(get("/beneficiaria/comprobante/donacion/200")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, benefSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idDonacion").value(200))
                .andExpect(jsonPath("$.estado").value("ENTREGADA"))
                .andExpect(jsonPath("$.receptorNombre").value("Marta López"));

        // 4. Reporte de entrega fallida
        mockMvc.perform(post("/beneficiaria/fallida")
                        .param("entidadId", "5")
                        .param("donacionId", "200")
                        .param("motivo", "Mercadería vencida")
                        .param("replanificable", "true")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, benefSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria"))
                .andExpect(flash().attributeExists("mensajeExito"));
    }

    @Test
    void testConfirmarRecepcionFallaCuandoChoferNoEstaEnPuerta() throws Exception {
        UserSession benefSession = new UserSession("comedor_esperanza", "Comedor Esperanza", "esperanza@comedor.org", "ROLE_BENEFICIARIA");
        benefSession.setEntidadId(5L);

        when(apiClientService.listarEntidades()).thenReturn(List.of(
                Map.of("id", 5L, "razonSocial", "Comedor Esperanza")
        ));
        when(apiClientService.listarDonaciones(any())).thenReturn(List.of(
                Map.of("id", 201L, "subcategoria", "Leche", "cantidad", 10, "unidadMedida", "L", "estado", "EN_TRASLADO",
                        "entidadAsignada", Map.of("id", 5L))
        ));
        when(apiClientService.confirmarEntregaPorDonacion(eq(201L), any(), any(), any(), any(), eq(5L))).thenReturn(false);

        mockMvc.perform(post("/beneficiaria/confirmar")
                        .param("entidadId", "5")
                        .param("donacionId", "201")
                        .param("receptorNombre", "Marta López")
                        .param("receptorDni", "28333444")
                        .param("fotoUrl", "https://img.test/recibido.jpg")
                        .sessionAttr(com.donatrack.frontend.security.AuthInterceptor.SESSION_USER_KEY, benefSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiaria?entidadId=5"))
                .andExpect(flash().attributeExists("mensajeError"));
    }
}


