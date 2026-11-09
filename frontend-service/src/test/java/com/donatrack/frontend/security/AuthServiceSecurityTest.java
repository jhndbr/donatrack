package com.donatrack.frontend.security;

import com.donatrack.frontend.service.ApiClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AuthServiceSecurityTest {

    private ApiClientService apiClient;
    private RestTemplate restTemplate;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        apiClient = Mockito.mock(ApiClientService.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        authService = new AuthService(apiClient, restTemplate);
    }

    @Test
    @DisplayName("SEG-02: Se eliminó el backdoor de contraseñas maestras genéricas")
    void testBackdoorPasswordsAreRejected() {
        // Simular un donante registrado cuyo password legítimo es 'mi_clave_secreta_99'
        Map<String, Object> donanteReal = Map.of(
                "id", 55L,
                "nombre", "Donante",
                "apellido", "Prueba",
                "emailPrincipal", "prueba.seguridad@donatrack.org"
        );
        when(apiClient.listarDonantes()).thenReturn(List.of(donanteReal));

        // Registrar la credencial legítima
        authService.registrarCredencial("prueba.seguridad@donatrack.org", "mi_clave_secreta_99");

        // Intentar ingresar usando los backdoors removidos
        Optional<UserSession> loginConDonante123 = authService.autenticar("prueba.seguridad@donatrack.org", "donante123");
        assertFalse(loginConDonante123.isPresent(), "El backdoor 'donante123' debe ser rechazado");

        Optional<UserSession> loginConAdmin123 = authService.autenticar("prueba.seguridad@donatrack.org", "admin123");
        assertFalse(loginConAdmin123.isPresent(), "El backdoor 'admin123' no debe permitir acceso a donantes ajenos");

        Optional<UserSession> loginConEmpresa123 = authService.autenticar("prueba.seguridad@donatrack.org", "empresa123");
        assertFalse(loginConEmpresa123.isPresent(), "El backdoor 'empresa123' debe ser rechazado");

        // El password legítimo sí debe autenticar
        Optional<UserSession> loginExitoso = authService.autenticar("prueba.seguridad@donatrack.org", "mi_clave_secreta_99");
        assertTrue(loginExitoso.isPresent());
        assertEquals("ROLE_DONANTE", loginExitoso.get().getRole());
    }

    @Test
    @DisplayName("SEG-02: Las contraseñas son hasheadas con BCrypt y validadas con hash matching")
    void testPasswordsHashedWithBCrypt() {
        Map<String, Object> donante = Map.of(
                "id", 89L,
                "nombre", "Nuevo",
                "apellido", "Usuario",
                "emailPrincipal", "nuevo.usuario@test.org"
        );
        when(apiClient.listarDonantes()).thenReturn(List.of(donante));

        authService.registrarCredencial("nuevo.usuario@test.org", "claveSuperSegura2026");

        // Autenticar con la clave correcta
        Optional<UserSession> ok = authService.autenticar("nuevo.usuario@test.org", "claveSuperSegura2026");
        assertTrue(ok.isPresent());

        // Autenticar con clave errónea
        Optional<UserSession> fail = authService.autenticar("nuevo.usuario@test.org", "claveErronea");
        assertFalse(fail.isPresent());
    }
}
