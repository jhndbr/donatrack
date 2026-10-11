package com.donatrack.frontend.controller;

import com.donatrack.frontend.security.AuthInterceptor;
import com.donatrack.frontend.security.AuthService;
import com.donatrack.frontend.security.UserSession;
import com.donatrack.frontend.service.ApiClientService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class HomeController {

    private final ApiClientService apiClient;
    private final AuthService authService;

    public HomeController(ApiClientService apiClient, AuthService authService) {
        this.apiClient = apiClient;
        this.authService = authService;
    }

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        List<Map<String, Object>> donaciones = apiClient.listarDonaciones(null);
        List<Map<String, Object>> entregadas = donaciones.stream()
                .filter(d -> "ENTREGADA".equals(d.get("estado")))
                .toList();

        double totalKilos = donaciones.stream()
                .filter(d -> d.get("cantidad") instanceof Number)
                .mapToDouble(d -> ((Number) d.get("cantidad")).doubleValue())
                .sum();

        model.addAttribute("totalDonaciones", donaciones.size());
        model.addAttribute("totalEntregadas", entregadas.size());
        model.addAttribute("totalKilos", totalKilos);
        model.addAttribute("donacionesDestacadas", donaciones.stream().limit(6).toList());
        model.addAttribute("donacionesEntregadas", entregadas);
        model.addAttribute("userSession", session.getAttribute(AuthInterceptor.SESSION_USER_KEY));

        return "index";
    }

    @GetMapping("/comprobantes")
    public String comprobantes(Model model, HttpSession session) {
        List<Map<String, Object>> entregadas = apiClient.listarDonaciones("ENTREGADA");
        model.addAttribute("entregadas", entregadas);
        model.addAttribute("userSession", session.getAttribute(AuthInterceptor.SESSION_USER_KEY));
        return "comprobantes";
    }

    @GetMapping("/privacidad")
    public String privacidad(Model model, HttpSession session) {
        model.addAttribute("userSession", session.getAttribute(AuthInterceptor.SESSION_USER_KEY));
        return "privacidad";
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "unauthorized", required = false) Boolean unauthorized,
                            @RequestParam(value = "denied", required = false) Boolean denied,
                            @RequestParam(value = "redirect", required = false) String redirect,
                            Model model,
                            HttpSession session) {
        // Si ya está logueado, redirigir a su panel correspondiente
        UserSession current = (UserSession) session.getAttribute(AuthInterceptor.SESSION_USER_KEY);
        if (current != null) {
            if (current.isAdmin()) return "redirect:/admin";
            if (current.isDonante()) return "redirect:/donante";
            if (current.isBeneficiaria()) return "redirect:/beneficiaria";
        }

        if (error != null) {
            model.addAttribute("errorMensaje", "Credenciales incorrectas. Verifique su usuario o correo y contraseña.");
        } else if (Boolean.TRUE.equals(unauthorized)) {
            model.addAttribute("errorMensaje", "Debe iniciar sesión para acceder a este panel.");
        } else if (Boolean.TRUE.equals(denied)) {
            model.addAttribute("errorMensaje", "No posee permisos suficientes para acceder a la sección solicitada.");
        }

        model.addAttribute("redirect", redirect);
        return "login";
    }

    @PostMapping("/login")
    public String procesarLogin(@RequestParam("username") String username,
                                @RequestParam("password") String password,
                                @RequestParam(value = "redirect", required = false) String redirect,
                                HttpSession session,
                                RedirectAttributes redirectAttrs) {
        Optional<UserSession> userSessionOpt = authService.autenticar(username, password);

        if (userSessionOpt.isEmpty()) {
            redirectAttrs.addAttribute("error", "bad_credentials");
            if (redirect != null && !redirect.isBlank()) {
                redirectAttrs.addAttribute("redirect", redirect);
            }
            return "redirect:/login";
        }

        UserSession userSession = userSessionOpt.get();
        session.setAttribute(AuthInterceptor.SESSION_USER_KEY, userSession);

        if (redirect != null && !redirect.isBlank() && !redirect.equals("/login")) {
            return "redirect:" + redirect;
        }

        // Redirección inteligente según el rol del usuario
        if (userSession.isAdmin()) {
            return "redirect:/admin";
        } else if (userSession.isDonante()) {
            return "redirect:/donante";
        } else {
            return "redirect:/beneficiaria";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttrs) {
        session.invalidate();
        redirectAttrs.addFlashAttribute("mensajeInfo", "Ha cerrado sesión correctamente.");
        return "redirect:/login";
    }

    @GetMapping("/registro")
    public String registroForm(Model model, HttpSession session) {
        model.addAttribute("userSession", session.getAttribute(AuthInterceptor.SESSION_USER_KEY));
        return "registro";
    }

    @PostMapping("/registro/donante")
    public String registrarDonante(@RequestParam("nombre") String nombre,
                                   @RequestParam("apellido") String apellido,
                                   @RequestParam("dni") String dni,
                                   @RequestParam(value = "edad", defaultValue = "25") Integer edad,
                                   @RequestParam("email") String email,
                                   @RequestParam(value = "telefono", required = false) String telefono,
                                   @RequestParam(value = "direccion", required = false) String direccion,
                                   RedirectAttributes redirectAttrs) {
        Map<String, Object> body = Map.of(
                "nombre", nombre,
                "apellido", apellido,
                "nroDocumento", dni,
                "edad", edad,
                "genero", "No especificado",
                "direccion", direccion != null ? direccion : "Sin dirección",
                "mediosContacto", List.of(
                        Map.of("tipo", "EMAIL", "valor", email, "esPredeterminado", true)
                )
        );

        Map<String, Object> nuevoDonante = apiClient.crearDonanteHumana(body);
        if (nuevoDonante != null) {
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Registro exitoso como Donante! Inicia sesión con tu correo (" + email + ") y tu contraseña (ej: donante123).");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo completar el registro. Verifique que el DNI no se encuentre registrado.");
        }
        return "redirect:/login";
    }

    @PostMapping("/registro/entidad")
    public String registrarEntidad(@RequestParam("razonSocial") String razonSocial,
                                   @RequestParam("direccion") String direccion,
                                   @RequestParam("telefono") String telefono,
                                   @RequestParam("email") String email,
                                   RedirectAttributes redirectAttrs) {
        Map<String, Object> body = Map.of(
                "razonSocial", razonSocial,
                "direccion", direccion,
                "telefono", telefono,
                "emailsRepresentantes", List.of(email)
        );

        Map<String, Object> nuevaEntidad = apiClient.crearEntidad(body);
        if (nuevaEntidad != null) {
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Registro exitoso para " + razonSocial + "! Inicia sesión con el email (" + email + ") y tu contraseña (ej: comedor123).");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la entidad beneficiaria. Intente nuevamente.");
        }
        return "redirect:/login";
    }
}
