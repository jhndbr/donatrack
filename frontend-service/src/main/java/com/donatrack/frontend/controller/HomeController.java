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
import org.springframework.web.bind.annotation.ResponseBody;
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

        List<Map<String, Object>> publicasMapa = apiClient.listarDonacionesPublicasMapa();

        List<Map<String, Object>> destacadas = donaciones.stream()
                .filter(this::esDeUltimoMes)
                .limit(6)
                .toList();

        model.addAttribute("totalDonaciones", donaciones.size());
        model.addAttribute("totalEntregadas", entregadas.size());
        model.addAttribute("totalKilos", totalKilos);
        model.addAttribute("donacionesDestacadas", destacadas);
        model.addAttribute("donacionesEntregadas", entregadas);
        model.addAttribute("donacionesPublicasMapa", publicasMapa);
        model.addAttribute("userSession", session.getAttribute(AuthInterceptor.SESSION_USER_KEY));

        return "index";
    }

    private boolean esDeUltimoMes(Map<String, Object> d) {
        if (d == null) return false;
        Object rawDate = d.get("fechaRegistro");
        if (rawDate == null) rawDate = d.get("fechaCreacion");
        if (rawDate == null) rawDate = d.get("fechaRecepcion");
        if (rawDate == null) return true;
        String str = rawDate.toString().trim();
        if (str.length() >= 10) {
            try {
                java.time.LocalDate fecha = java.time.LocalDate.parse(str.substring(0, 10));
                return !fecha.isBefore(java.time.LocalDate.now().minusDays(30));
            } catch (Exception ignored) {
                return true;
            }
        }
        return true;
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

        if (redirect != null && !redirect.isBlank() && redirect.startsWith("/") && !redirect.startsWith("//") && !redirect.equals("/login")) {
            return "redirect:" + redirect;
        }

        // Redirección inteligente según el rol del usuario
        if (userSession.isAdmin()) {
            return "redirect:/admin";
        } else if (userSession.isChofer()) {
            return "redirect:/chofer";
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

    /**
     * Endpoint AJAX para validación en tiempo real de direcciones argentinas (Georef AR).
     */
    @GetMapping("/api/direcciones/validar")
    @ResponseBody
    public Map<String, Object> validarDireccion(@RequestParam(value = "direccion", required = false) String direccion) {
        if (direccion == null || direccion.trim().isBlank()) {
            return Map.of("esValida", false, "mensajeValidacion", "Ingrese una dirección para validar.");
        }
        return apiClient.normalizarDireccion(direccion.trim());
    }

    @PostMapping("/registro/donante")
    public String registrarDonante(@RequestParam("nombre") String nombre,
                                   @RequestParam("apellido") String apellido,
                                   @RequestParam("dni") String dni,
                                   @RequestParam(value = "edad", defaultValue = "25") Integer edad,
                                   @RequestParam("email") String email,
                                   @RequestParam(value = "password", defaultValue = "donante123") String password,
                                   @RequestParam(value = "telefono", required = false) String telefono,
                                   @RequestParam(value = "medioPreferido", defaultValue = "EMAIL") String medioPreferido,
                                   @RequestParam(value = "direccion", required = false) String direccion,
                                   RedirectAttributes redirectAttrs) {

        String dirFinal = (direccion != null && !direccion.isBlank()) ? direccion.trim() : "Sin dirección";
        if (!dirFinal.equalsIgnoreCase("Sin dirección")) {
            Map<String, Object> norm = apiClient.normalizarDireccion(dirFinal);
            if (norm != null && Boolean.FALSE.equals(norm.get("esValida"))) {
                redirectAttrs.addFlashAttribute("mensajeError", "Dirección no válida: " + norm.get("mensajeValidacion"));
                return "redirect:/registro";
            }
            if (norm != null && norm.get("nomenclatura") != null) {
                dirFinal = (String) norm.get("nomenclatura");
            }
        }

        boolean tieneTelefono = telefono != null && !telefono.trim().isEmpty();
        boolean prefTelefono = tieneTelefono && "TELEFONO".equalsIgnoreCase(medioPreferido);
        java.util.List<Map<String, Object>> mediosContacto = new java.util.ArrayList<>();
        mediosContacto.add(Map.of("tipo", "EMAIL", "valor", email, "esPredeterminado", !prefTelefono));
        if (tieneTelefono) {
            mediosContacto.add(Map.of("tipo", "TELEFONO", "valor", telefono.trim(), "esPredeterminado", prefTelefono));
        }

        Map<String, Object> body = Map.of(
                "nombre", nombre,
                "apellido", apellido,
                "nroDocumento", dni,
                "edad", edad,
                "genero", "No especificado",
                "direccion", dirFinal,
                "mediosContacto", mediosContacto
        );

        Map<String, Object> nuevoDonante = apiClient.crearDonanteHumana(body);
        if (nuevoDonante != null) {
            authService.registrarCredencial(email, password);
            if (dni != null && !dni.isBlank()) {
                authService.registrarCredencial(dni, password);
            }
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Registro exitoso como Donante! Inicia sesión con tu correo (" + email + ") y la contraseña elegida.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo completar el registro. Verifique que el DNI no se encuentre registrado.");
        }
        return "redirect:/login";
    }

    @PostMapping("/registro/juridica")
    public String registrarDonanteJuridico(@RequestParam("razonSocial") String razonSocial,
                                          @RequestParam("cuit") String cuit,
                                          @RequestParam(value = "tipo", defaultValue = "Empresa") String tipo,
                                          @RequestParam(value = "rubro", defaultValue = "General") String rubro,
                                          @RequestParam("email") String email,
                                          @RequestParam(value = "password", defaultValue = "empresa123") String password,
                                          @RequestParam(value = "telefono", required = false) String telefono,
                                          @RequestParam(value = "medioPreferido", defaultValue = "EMAIL") String medioPreferido,
                                          @RequestParam(value = "representante", required = false) String representante,
                                          @RequestParam(value = "direccion", required = false) String direccion,
                                          RedirectAttributes redirectAttrs) {

        String dirFinal = (direccion != null && !direccion.isBlank()) ? direccion.trim() : "Sin dirección";
        if (!dirFinal.equalsIgnoreCase("Sin dirección")) {
            Map<String, Object> norm = apiClient.normalizarDireccion(dirFinal);
            if (norm != null && Boolean.FALSE.equals(norm.get("esValida"))) {
                redirectAttrs.addFlashAttribute("mensajeError", "Dirección no válida: " + norm.get("mensajeValidacion"));
                return "redirect:/registro";
            }
            if (norm != null && norm.get("nomenclatura") != null) {
                dirFinal = (String) norm.get("nomenclatura");
            }
        }

        boolean tieneTelefono = telefono != null && !telefono.trim().isEmpty();
        boolean prefTelefono = tieneTelefono && "TELEFONO".equalsIgnoreCase(medioPreferido);
        java.util.List<Map<String, Object>> mediosContacto = new java.util.ArrayList<>();
        mediosContacto.add(Map.of("tipo", "EMAIL", "valor", email, "esPredeterminado", !prefTelefono));
        if (tieneTelefono) {
            mediosContacto.add(Map.of("tipo", "TELEFONO", "valor", telefono.trim(), "esPredeterminado", prefTelefono));
        }

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("razonSocial", razonSocial);
        body.put("cuit", cuit);
        body.put("tipo", tipo);
        body.put("rubro", rubro);
        body.put("direccion", dirFinal);
        body.put("mediosContacto", mediosContacto);
        if (representante != null && !representante.trim().isEmpty()) {
            body.put("representantes", List.of(representante.trim()));
        }

        Map<String, Object> nuevoDonante = apiClient.crearDonanteJuridica(body);
        if (nuevoDonante != null) {
            authService.registrarCredencial(email, password);
            if (cuit != null && !cuit.isBlank()) {
                authService.registrarCredencial(cuit, password);
            }
            if (razonSocial != null && !razonSocial.isBlank()) {
                authService.registrarCredencial(razonSocial, password);
            }
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Registro exitoso como Empresa/Institución donante! Inicia sesión con tu correo (" + email + ") o CUIT.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo completar el registro. Verifique que el CUIT no se encuentre registrado.");
        }
        return "redirect:/login";
    }

    @PostMapping("/registro/entidad")
    public String registrarEntidad(@RequestParam("razonSocial") String razonSocial,
                                   @RequestParam("direccion") String direccion,
                                   @RequestParam("telefono") String telefono,
                                   @RequestParam("email") String email,
                                   @RequestParam(value = "password", defaultValue = "comedor123") String password,
                                   RedirectAttributes redirectAttrs) {

        if (direccion == null || direccion.trim().isBlank()) {
            redirectAttrs.addFlashAttribute("mensajeError", "La dirección de la sede o comedor es obligatoria para la logística de entregas.");
            return "redirect:/registro";
        }

        String dirTrim = direccion.trim();
        Map<String, Object> norm = apiClient.normalizarDireccion(dirTrim);
        if (norm != null && Boolean.FALSE.equals(norm.get("esValida"))) {
            redirectAttrs.addFlashAttribute("mensajeError", "Dirección de la entidad inválida: " + norm.get("mensajeValidacion"));
            return "redirect:/registro";
        }
        if (norm != null && norm.get("nomenclatura") != null) {
            dirTrim = (String) norm.get("nomenclatura");
        }

        Map<String, Object> body = Map.of(
                "razonSocial", razonSocial,
                "direccion", dirTrim,
                "telefono", telefono,
                "emailsRepresentantes", List.of(email)
        );

        Map<String, Object> nuevaEntidad = apiClient.crearEntidad(body);
        if (nuevaEntidad != null) {
            authService.registrarCredencial(email, password);
            redirectAttrs.addFlashAttribute("mensajeExito", "¡Registro exitoso para " + razonSocial + "! Inicia sesión con el email (" + email + ") y la contraseña elegida.");
        } else {
            redirectAttrs.addFlashAttribute("mensajeError", "No se pudo registrar la entidad beneficiaria. Intente nuevamente.");
        }
        return "redirect:/login";
    }
}
