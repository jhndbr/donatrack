package com.donatrack.notificaciones.service;

import com.donatrack.common.enums.CanalNotificacion;
import com.donatrack.common.events.*;
import com.donatrack.notificaciones.domain.AuditoriaNotificacion;
import com.donatrack.notificaciones.domain.EstadoNotificacion;
import com.donatrack.notificaciones.domain.RegistroEventoNotificacion;
import com.donatrack.notificaciones.repository.AuditoriaNotificacionRepository;
import com.donatrack.notificaciones.repository.RegistroEventoNotificacionRepository;
import com.donatrack.notificaciones.sender.NotificationDispatcher;
import com.donatrack.notificaciones.template.NotificationTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ====================================================================================================
 * SERVICIO ORQUESTADOR CENTRAL DE NOTIFICACIONES (notificaciones-service) - DonaTrack
 * ====================================================================================================
 * Orquesta la transformación de eventos de dominio a comunicaciones multicanal enriquecidas (HTML/SMS/WhatsApp),
 * respetando el canal preferido del donante, garantizando control de idempotencia (FT-06), auditoría
 * relacional de envíos (CU-56, FT-07) y alertas prioritarias a administración ante incidencias operativas (CU-61, CU-45).
 */
@Service
public class NotificacionesService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificationDispatcher dispatcher;
    private final NotificationTemplateService templateService;
    private final AuditoriaNotificacionRepository auditoriaRepo;
    private final RegistroEventoNotificacionRepository eventoRepo;

    @Value("${donatrack.notificaciones.admin-email:admin@donatrack.org}")
    private String adminEmail = "admin@donatrack.org";

    @Value("${donatrack.frontend.url:http://localhost:8086}")
    private String frontendBaseUrl = "http://localhost:8086";

    public NotificacionesService(NotificationDispatcher dispatcher,
                                 NotificationTemplateService templateService,
                                 AuditoriaNotificacionRepository auditoriaRepo,
                                 RegistroEventoNotificacionRepository eventoRepo) {
        this.dispatcher = dispatcher;
        this.templateService = templateService;
        this.auditoriaRepo = auditoriaRepo;
        this.eventoRepo = eventoRepo;
    }

    /**
     * Procesa un despacho aplicando control de idempotencia (FT-06) y persistencia de auditoría (CU-56, FT-07).
     * El registro de unicidad se guarda ÚNICAMENTE si el despacho resultó exitoso, permitiendo reintentos ante fallas.
     */
    @Transactional
    public boolean despacharConAuditoria(String claveEvento,
                                         String tipoEvento,
                                         CanalNotificacion canal,
                                         String destinatario,
                                         String asunto,
                                         String mensaje) {
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("Destinatario ausente para evento '{}'. Se omite despacho.", tipoEvento);
            return false;
        }

        // 1. Control de Idempotencia
        if (claveEvento != null && eventoRepo.existsByClaveEvento(claveEvento)) {
            log.info("Notificaciones: Evento ya procesado previamente (idempotencia activa): {}", claveEvento);
            auditoriaRepo.save(new AuditoriaNotificacion(
                    claveEvento, destinatario, canal, asunto, mensaje,
                    EstadoNotificacion.DUPLICADO_IGNORADO, "Evento descartado por control de unicidad"
            ));
            return false;
        }

        // 2. Despacho por el canal correspondiente
        boolean exito = false;
        String errorDetalle = null;
        try {
            exito = dispatcher.despachar(canal, destinatario, asunto, mensaje);
            if (!exito) {
                errorDetalle = "El adaptador de envío retornó falso o no pudo despachar el mensaje.";
            }
        } catch (Exception e) {
            log.error("Excepción durante despacho a {}: {}", destinatario, e.getMessage(), e);
            errorDetalle = e.getMessage();
            exito = false;
        }

        // 3. Auditoría del intento (FT-07)
        EstadoNotificacion estado = exito ? EstadoNotificacion.ENVIADO : EstadoNotificacion.FALLIDO;
        auditoriaRepo.save(new AuditoriaNotificacion(
                claveEvento, destinatario, canal, asunto, mensaje, estado, errorDetalle
        ));

        // 4. Registro de unicidad: SOLO si fue exitoso para permitir reintentos AMQP si falló
        if (claveEvento != null && exito) {
            eventoRepo.save(new RegistroEventoNotificacion(claveEvento, tipoEvento));
        }

        return exito;
    }

    /**
     * CU-58: Notificar asignación de donación a donante y entidad beneficiaria.
     */
    public void notificarDonacionAsignada(DonacionAsignadaEvent evento) {
        if (evento == null) return;
        log.info("Notificaciones: Procesando donacion.asignada idDonacion={}", evento.getIdDonacion());

        // 1. Notificación al Donante (respetando su canal preferido)
        CanalNotificacion canalDonante = resolverCanal(evento.getCanalPreferidoDonante());
        String destDonante = resolverDestinatario(canalDonante, evento.getContactoPreferidoDonante(), evento.getEmailDonante());

        if (destDonante != null && !destDonante.isBlank()) {
            String clave = "notif-donacion-asignada-donante-" + evento.getIdDonacion() + "-" + evento.getIdDonante();
            String asunto = "¡Tu donación ha sido asignada a una entidad beneficiaria!";
            Map<String, String> detalles = new LinkedHashMap<>();
            detalles.put("Entidad Beneficiaria", evento.getNombreEntidad());
            detalles.put("Bienes Donados", String.format("%.2f %s de %s", evento.getCantidad(), evento.getUnidadMedida(), evento.getSubcategoria()));
            detalles.put("Estado", "Asignación realizada (Lista para programar entrega)");

            String contenido = formatearContenido(
                    canalDonante,
                    asunto,
                    "¡Muchas gracias " + evento.getNombreDonante() + "!",
                    "Tu aporte solidario ya tiene destino confirmado. La logística de DonaTrack coordinará la recolección y entrega a la brevedad.",
                    detalles,
                    "Ver Mis Donaciones",
                    frontendBaseUrl + "/donante/donaciones"
            );

            despacharConAuditoria(clave, "DONACION_ASIGNADA", canalDonante, destDonante, asunto, contenido);
        }

        // 2. Notificación a la Entidad Beneficiaria (siempre email institucional)
        if (evento.getEmailEntidad() != null && !evento.getEmailEntidad().isBlank()) {
            String clave = "notif-donacion-asignada-entidad-" + evento.getIdDonacion() + "-" + evento.getIdEntidadBeneficiaria();
            String asunto = "¡Nueva donación asignada a tu entidad en DonaTrack!";
            Map<String, String> detalles = new LinkedHashMap<>();
            detalles.put("Donante Solidario", evento.getNombreDonante());
            detalles.put("Detalle de Bienes", String.format("%.2f %s de %s", evento.getCantidad(), evento.getUnidadMedida(), evento.getSubcategoria()));
            detalles.put("Próximo Paso", "Te informaremos cuando el camión comice el recorrido de entrega.");

            String html = templateService.generarPlantillaHtml(
                    asunto,
                    "Estimados miembros de " + evento.getNombreEntidad() + ",",
                    "Se les ha asignado una nueva donación de bienes para su causa. Podrán recibirla próximamente a través de nuestro circuito logístico.",
                    detalles,
                    "Acceder al Panel de Entidad",
                    frontendBaseUrl + "/beneficiaria"
            );

            despacharConAuditoria(clave, "DONACION_ASIGNADA", CanalNotificacion.EMAIL, evento.getEmailEntidad(), asunto, html);
        }
    }

    /**
     * CU-57: Notificar inactividad persuasiva a donantes (>20 días sin donar).
     */
    public void notificarDonanteInactivo(DonanteInactivoEvent evento) {
        if (evento == null) return;
        log.info("Notificaciones: Procesando donante.inactivo idDonante={}", evento.getIdDonante());

        CanalNotificacion canal = resolverCanal(evento.getCanalPreferido());
        String destinatario = resolverDestinatario(canal, evento.getContactoPreferido(), evento.getEmail());
        if (destinatario == null || destinatario.isBlank()) return;

        LocalDate hoy = LocalDate.now();
        String clave = "notif-donante-inactivo-" + evento.getIdDonante() + "-" + hoy.getYear() + "-" + hoy.getMonthValue();
        String asunto = "¡Te extrañamos en DonaTrack! Ayúdanos a seguir sumando sonrisas";

        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Días desde tu última ayuda", String.valueOf(evento.getDiasInactivo()) + " días");
        detalles.put("Impacto Solidario", "Comedores y merenderos siguen necesitando tu apoyo");

        String contenido = formatearContenido(
                canal,
                asunto,
                "Hola " + evento.getNombre() + ",",
                "Han pasado más de 20 días desde tu última colaboración. Muchas organizaciones comunitarias dependen del compromiso solidario de personas como tú para continuar su labor diaria.",
                detalles,
                "Realizar una Nueva Donación",
                frontendBaseUrl + "/donante/nueva-donacion"
        );

        despacharConAuditoria(clave, "DONANTE_INACTIVO", canal, destinatario, asunto, contenido);
    }

    /**
     * Notificación de Bienvenida al registrar un nuevo Donante (manual o vía CSV).
     */
    public void notificarBienvenidaDonante(DonanteRegistradoEvent evento) {
        if (evento == null) return;
        log.info("Notificaciones: Procesando bienvenida donante.registrado idDonante={}", evento.getIdDonante());

        CanalNotificacion canal = resolverCanal(evento.getCanalPreferido());
        String destinatario = resolverDestinatario(canal, evento.getContactoPreferido(), evento.getEmailDonante());
        if (destinatario == null || destinatario.isBlank()) return;

        String clave = "notif-bienvenida-donante-" + evento.getIdDonante();
        String asunto = "¡Bienvenido/a a DonaTrack, " + evento.getNombreDonante() + "!";

        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Nombre", evento.getNombreDonante());
        if (evento.getUsernameGenerado() != null && !evento.getUsernameGenerado().isBlank()) {
            detalles.put("Usuario de Acceso", evento.getUsernameGenerado());
        }
        if (evento.getPasswordTemporal() != null && !evento.getPasswordTemporal().isBlank()) {
            detalles.put("Contraseña Inicial", evento.getPasswordTemporal());
        }
        detalles.put("Canal Notificación", canal.name());

        String contenido = formatearContenido(
                canal,
                asunto,
                "¡Hola " + evento.getNombreDonante() + "!",
                "Gracias por registrarte en DonaTrack. Desde nuestra plataforma podrás gestionar tus donaciones solidarias y seguir el recorrido de tus aportes en tiempo real hasta su entrega final.",
                detalles,
                "Ingresar a la Plataforma",
                frontendBaseUrl + "/login"
        );

        despacharConAuditoria(clave, "DONANTE_REGISTRADO", canal, destinatario, asunto, contenido);
    }

    /**
     * CU-61: Notificar inicio de ruta logística con enlace interactivo al mapa en tiempo real.
     */
    public void notificarRutaIniciada(RutaIniciadaEvent evento) {
        if (evento == null) return;
        log.info("Notificaciones: Procesando ruta.iniciada idRuta={}", evento.getIdRuta());

        String asunto = "¡El camión ha iniciado el reparto de tu donación!";
        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Identificador de Ruta", evento.getIdRuta());
        detalles.put("Patente del Camión", evento.getPatenteCamion());
        detalles.put("Seguimiento en Vivo", "Ubicación GPS actualizada en tiempo real");

        String link = (evento.getLinkSeguimiento() != null && !evento.getLinkSeguimiento().isBlank()
                && !evento.getLinkSeguimiento().startsWith("https://donatrack.org"))
                ? evento.getLinkSeguimiento()
                : frontendBaseUrl + "/donante/tracking";

        String html = templateService.generarPlantillaHtml(
                asunto,
                "¡Buenas noticias!",
                "El camión asignado ha iniciado formalmente su recorrido. Puedes seguir el avance del trayecto directamente en nuestro mapa interactivo.",
                detalles,
                "Ver Mapa de Seguimiento en Tiempo Real",
                link
        );

        if (evento.getEmailsNotificar() != null) {
            for (String email : evento.getEmailsNotificar()) {
                if (email != null && !email.isBlank()) {
                    String clave = "notif-ruta-iniciada-" + evento.getIdRuta() + "-" + email.trim().toLowerCase();
                    despacharConAuditoria(clave, "RUTA_INICIADA", CanalNotificacion.EMAIL, email, asunto, html);
                }
            }
        }
    }

    /**
     * CU-61: Notificar entrega confirmada con comprobante digital y certificado formal de recepción.
     */
    public void notificarEntregaConfirmada(EntregaConfirmadaEvent evento) {
        if (evento == null) return;
        log.info("Notificaciones: Procesando entrega.confirmada idEntrega={}", evento.getIdEntrega());

        String asunto = "¡Donación entregada con éxito! Comprobante oficial disponible";
        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Código de Entrega", evento.getIdEntrega());
        detalles.put("Camión de Reparto", evento.getPatenteCamion());
        String fechaHora = (evento.getTimestamp() != null)
                ? evento.getTimestamp().format(DATE_TIME_FORMATTER)
                : java.time.LocalDateTime.now().format(DATE_TIME_FORMATTER);
        detalles.put("Fecha y Hora de Entrega", fechaHora);
        detalles.put("Certificado de Recepción", "Firmado formalmente en destino");

        String link = (evento.getComprobanteUrl() != null && !evento.getComprobanteUrl().isBlank()
                && !evento.getComprobanteUrl().startsWith("https://donatrack.org"))
                ? evento.getComprobanteUrl()
                : frontendBaseUrl + "/beneficiaria/notificaciones";

        // Donante (canal preferido)
        CanalNotificacion canalDonante = resolverCanal(evento.getCanalPreferidoDonante());
        String destDonante = resolverDestinatario(canalDonante, evento.getContactoPreferidoDonante(), evento.getEmailDonante());

        if (destDonante != null && !destDonante.isBlank()) {
            String clave = "notif-entrega-confirmada-donante-" + evento.getIdEntrega();
            String contenidoDonante = formatearContenido(
                    canalDonante,
                    asunto,
                    "¡Misión Cumplida!",
                    "Nos alegra confirmar que la donación ha sido entregada exitosamente en la sede de la entidad beneficiaria. Puedes consultar y descargar el comprobante de recepción.",
                    detalles,
                    "Ver Comprobante Digital",
                    link
            );
            despacharConAuditoria(clave, "ENTREGA_CONFIRMADA", canalDonante, destDonante, asunto, contenidoDonante);
        }

        // Entidad Beneficiaria (EMAIL)
        if (evento.getEmailEntidad() != null && !evento.getEmailEntidad().isBlank()) {
            String clave = "notif-entrega-confirmada-entidad-" + evento.getIdEntrega();
            String htmlEntidad = templateService.generarPlantillaHtml(
                    asunto,
                    "¡Misión Cumplida!",
                    "Confirmamos la recepción de la donación asignada a su entidad. El comprobante de entrega se encuentra disponible para su archivo institucional.",
                    detalles,
                    "Ver Comprobante Digital",
                    link
            );
            despacharConAuditoria(clave, "ENTREGA_CONFIRMADA", CanalNotificacion.EMAIL, evento.getEmailEntidad(), asunto, htmlEntidad);
        }
    }

    /**
     * CU-61, CU-45, TODO-NOT-06: Notificar entrega fallida a donante, entidad y alerta prioritaria a administración.
     */
    public void notificarEntregaFallida(EntregaFallidaEvent evento) {
        if (evento == null) return;
        log.info("Notificaciones: Procesando entrega.fallida idEntrega={}", evento.getIdEntrega());

        String asunto = "Aviso importante: Incidencia en la entrega programada";
        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Código de Entrega", evento.getIdEntrega());
        detalles.put("Motivo Registrado", evento.getMotivoFalla());
        detalles.put("Replanificable", evento.isReplanificable() ? "Sí, los bienes regresan a depósito" : "No replanificable");

        // Donante (canal preferido)
        CanalNotificacion canalDonante = resolverCanal(evento.getCanalPreferidoDonante());
        String destDonante = resolverDestinatario(canalDonante, evento.getContactoPreferidoDonante(), evento.getEmailDonante());

        if (destDonante != null && !destDonante.isBlank()) {
            String clave = "notif-entrega-fallida-donante-" + evento.getIdEntrega();
            String contenidoDonante = formatearContenido(
                    canalDonante,
                    asunto,
                    "Estimado/a donante,",
                    "Lamentamos informarte que la entrega no pudo concretarse según lo planificado. El equipo operativo ha registrado la justificación correspondiente.",
                    detalles,
                    "Consultar Historial de Donación",
                    frontendBaseUrl + "/donante/donaciones"
            );
            despacharConAuditoria(clave, "ENTREGA_FALLIDA", canalDonante, destDonante, asunto, contenidoDonante);
        }

        // Entidad Beneficiaria (EMAIL)
        if (evento.getEmailEntidad() != null && !evento.getEmailEntidad().isBlank()) {
            String clave = "notif-entrega-fallida-entidad-" + evento.getIdEntrega();
            String htmlEntidad = templateService.generarPlantillaHtml(
                    asunto,
                    "Estimados miembros de la entidad,",
                    "Lamentamos informarle que la entrega programada no pudo concretarse. El equipo de logística gestionará la reprogramación correspondiente.",
                    detalles,
                    "Consultar Entregas",
                    frontendBaseUrl + "/beneficiaria"
            );
            despacharConAuditoria(clave, "ENTREGA_FALLIDA", CanalNotificacion.EMAIL, evento.getEmailEntidad(), asunto, htmlEntidad);
        }

        // ALERTA PRIORITARIA A ADMINISTRADORES (CU-45, CU-61, TODO-NOT-06)
        if (adminEmail != null && !adminEmail.isBlank()) {
            String claveAdmin = "notif-entrega-fallida-admin-" + evento.getIdEntrega();
            String asuntoAdmin = "[ALERTA OPERATIVA] Incidencia de Entrega Fallida en Donación #" + evento.getIdDonacion();
            Map<String, String> detallesAdmin = new LinkedHashMap<>(detalles);
            detallesAdmin.put("Donación ID", String.valueOf(evento.getIdDonacion()));
            detallesAdmin.put("Acción Requerida", "Revisar justificación del chofer y replanificar ruta (CU-45)");

            String htmlAdmin = templateService.generarPlantillaHtml(
                    asuntoAdmin,
                    "Atención: Equipo de Administración de DonaTrack,",
                    "Se ha registrado una entrega no concretada durante la jornada logística. Es necesaria la intervención administrativa para gestionar la incidencia.",
                    detallesAdmin,
                    "Gestionar Incidencia en Panel Admin",
                    frontendBaseUrl + "/admin/notificaciones"
            );

            despacharConAuditoria(claveAdmin, "ENTREGA_FALLIDA_ADMIN", CanalNotificacion.EMAIL, adminEmail, asuntoAdmin, htmlAdmin);
        }
    }

    /**
     * CU-59: Notificar misión cumplida e insignia desbloqueada al donante.
     */
    public void notificarMisionCumplida(MisionCumplidaEvent evento) {
        if (evento == null) return;
        log.info("Notificaciones: Procesando mision.cumplida donante={}", evento.getNombreDonante());

        CanalNotificacion canal = resolverCanal(evento.getCanalPreferido());
        String destinatario = resolverDestinatario(canal, evento.getContactoPreferido(), evento.getEmailDonante());
        if (destinatario == null || destinatario.isBlank()) return;

        String insigniaKey = (evento.getNombreInsignia() != null) ? evento.getNombreInsignia().replaceAll("\\s+", "_") : "insignia";
        String clave = "notif-mision-cumplida-" + evento.getIdDonante() + "-" + insigniaKey;
        String asunto = "¡Felicitaciones! Has desbloqueado una nueva insignia en DonaTrack";

        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Misión Cumplida", evento.getTipoMision());
        detalles.put("Insignia Obtenida", "🏅 " + evento.getNombreInsignia());
        detalles.put("Detalle del Logro", evento.getDescripcionInsignia());
        detalles.put("Categoría Actual", evento.getCategoriaActual());

        String contenido = formatearContenido(
                canal,
                asunto,
                "¡Bravo " + evento.getNombreDonante() + "!",
                "Tu compromiso y constancia solidaria te han permitido completar una nueva misión. ¡Gracias por hacer una diferencia real!",
                detalles,
                "Ver Mis Insignias y Logros",
                frontendBaseUrl + "/donante/incentivos"
        );

        despacharConAuditoria(clave, "MISION_CUMPLIDA", canal, destinatario, asunto, contenido);
    }

    /**
     * CU-60: Notificar ascenso de categoría de donante.
     */
    public void notificarCategoriaAscendida(CategoriaAscendidaEvent evento) {
        if (evento == null) return;
        log.info("Notificaciones: Procesando categoria.ascendida donante={}", evento.getNombreDonante());

        CanalNotificacion canal = resolverCanal(evento.getCanalPreferido());
        String destinatario = resolverDestinatario(canal, evento.getContactoPreferido(), evento.getEmailDonante());
        if (destinatario == null || destinatario.isBlank()) return;

        String clave = "notif-categoria-ascendida-" + evento.getIdDonante() + "-" + evento.getNuevaCategoria();
        String asunto = "¡Has subido de nivel en DonaTrack!";

        Map<String, String> detalles = new LinkedHashMap<>();
        detalles.put("Nivel Anterior", evento.getCategoriaPrevia());
        detalles.put("Nuevo Rango Oficial", "⭐ " + evento.getNuevaCategoria());
        detalles.put("Reconocimiento", "Tu categoría se destaca públicamente en tus aportes");

        String contenido = formatearContenido(
                canal,
                asunto,
                "¡Felicitaciones " + evento.getNombreDonante() + "!",
                "Has completado todas las metas de tu nivel anterior y has ascendido a la categoría " + evento.getNuevaCategoria() + ". Tu impacto solidario continúa transformando vidas.",
                detalles,
                "Explorar Próximos Desafíos",
                frontendBaseUrl + "/donante/incentivos"
        );

        despacharConAuditoria(clave, "CATEGORIA_ASCENDIDA", canal, destinatario, asunto, contenido);
    }

    // --- Métodos Auxiliares de Resolución Polimórfica ---

    private CanalNotificacion resolverCanal(Object canalObj) {
        if (canalObj instanceof CanalNotificacion cn) {
            return cn;
        }
        if (canalObj instanceof String str && !str.isBlank()) {
            try {
                return CanalNotificacion.valueOf(str.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return CanalNotificacion.EMAIL;
    }

    private String resolverDestinatario(CanalNotificacion canal, String contactoPreferido, String emailFallback) {
        if (canal != CanalNotificacion.EMAIL && contactoPreferido != null && !contactoPreferido.isBlank()) {
            return contactoPreferido;
        }
        if (emailFallback != null && !emailFallback.isBlank()) {
            return emailFallback;
        }
        return contactoPreferido;
    }

    private String formatearContenido(CanalNotificacion canal,
                                      String titulo,
                                      String saludo,
                                      String mensajePrincipal,
                                      Map<String, String> detalles,
                                      String botonTexto,
                                      String botonUrl) {
        if (canal == CanalNotificacion.EMAIL) {
            return templateService.generarPlantillaHtml(titulo, saludo, mensajePrincipal, detalles, botonTexto, botonUrl);
        } else {
            return templateService.generarTextoPlano(titulo, saludo, mensajePrincipal, detalles, botonUrl);
        }
    }
}
