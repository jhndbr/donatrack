package com.donatrack.notificaciones.sender;

import com.donatrack.common.enums.CanalNotificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ====================================================================================================
 * DESPACHADOR Y SERVICIO CENTRAL DE NOTIFICACIONES (notificaciones-service) - DonaTrack
 * ====================================================================================================
 *
 * ----------------------------------------------------------------------------------------------------
 * 1. FLUJOS PRINCIPALES RESUMIDOS (Casos de Uso de Notificaciones)
 * ----------------------------------------------------------------------------------------------------
 *
 * FLUJO A: Despacho Polimórfico Multicanal (CU-56)
 *   1. Se recibe solicitud de notificación desde los consumidores AMQP o servicios de aplicación.
 *   2. Se identifica el canal preferido (EMAIL, SMS, WHATSAPP) y los datos de destino.
 *   3. El despachador resuelve en tiempo de ejecución la estrategia adecuada (Strategy / Factory Pattern).
 *   4. Se ejecuta el envío (Mailpit SMTP para correo, adaptadores de SMS y WhatsApp).
 *   5. Se audita el resultado del intento sin interrumpir las transacciones del negocio origen (FT-07).
 *
 * FLUJO B: Notificación de Inactividad de Donantes (CU-57)
 *   1. Scheduler de donaciones detecta >20 días sin donar y emite donante.inactivo.
 *   2. Notificaciones envía correo/mensaje personalizado destacando su impacto solidario previo para reactivarlo.
 *
 * FLUJO C: Notificación de Asignación de Donación (CU-58)
 *   1. Evento donacion.asignada recibido tras confirmación administrativa.
 *   2. Se envía mensaje al donante informando la entidad beneficiaria que recibirá sus bienes.
 *   3. Se envía mensaje a la entidad beneficiaria detallando la subcategoría, cantidad y fecha estimada.
 *
 * FLUJO D: Notificaciones de Ludificación y Progreso (CU-59, CU-60)
 *   1. Evento mision.cumplida: felicitación al donante con insignia desbloqueada y puntos sumados.
 *   2. Evento categoria.ascendida: aviso de ascenso de nivel (Colaborador -> Sostenedor -> Transformador).
 *
 * FLUJO E: Notificaciones del Ciclo Logístico (CU-61)
 *   1. Evento ruta.iniciada: aviso a donantes y beneficiarias con enlace interactivo al mapa en tiempo real.
 *   2. Evento entrega.confirmada: certificado formal de recepción con fecha, hora, camión y comprobante.
 *   3. Evento entrega.fallida: aviso a entidad, donante y administradores con justificación obligatoria y reprogramación.
 *
 * ----------------------------------------------------------------------------------------------------
 * 2. LO NECESARIO DEL SERVICE (Requerimientos Técnicos y de Dominio)
 * ----------------------------------------------------------------------------------------------------
 * - Desacoplamiento total: consumidor AMQP puro sin acoplamiento sincrónico.
 * - Despacho polimórfico extensible (Open-Closed Principle / Dependency Inversion).
 * - Servidor SMTP Mailpit para previsualización HTML (localhost:1025 / UI 8025).
 * - Adaptadores listos para producción para Twilio (SMS) y Meta Cloud API (WhatsApp).
 * - Resiliencia ante fallos: Dead Letter Queue (DLQ) e idempotencia para mensajes repetidos (FT-06, FT-07).
 *
 * ----------------------------------------------------------------------------------------------------
 * 3. TO-DO MAESTRO: LO QUE LE FALTA AL SERVICE
 * ----------------------------------------------------------------------------------------------------
 * [ ] TODO-NOT-01 (CU-56..CU-61): Implementar motor de plantillas HTML (Thymeleaf / FreeMarker) con diseño
 *     institucional unificado para correos de bienvenida, asignación, inicio de ruta y comprobante de entrega.
 * [ ] TODO-NOT-02 (CU-56): Implementar tabla/registro de auditoria de notificaciones (destinatario, canal,
 *     asunto, payload, estado ENVIADO/FALLIDO, timestamp y reintentos).
 * [ ] TODO-NOT-03 (FT-06): Implementar filtro de deduplicación e idempotencia (evitar enviar correos dobles
 *     si RabbitMQ reentrega el mensaje tras un reintento de red).
 * [ ] TODO-NOT-04 (CU-56): Conectar adaptadores de producción para SMS (Twilio REST API) y WhatsApp (Meta Cloud API).
 * [ ] TODO-NOT-05 (CU-61): En entrega fallida, asegurar el despacho de alerta urgente a los administradores
 *     del sistema además del donante y la entidad beneficiaria.
 * [ ] TODO-NOT-06 (CU-57): Formatear mensaje persuasivo de reactivación tras 20 días de inactividad con métricas históricas.
 * ====================================================================================================
 */
@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final Map<CanalNotificacion, NotificationSender> senderMap = new EnumMap<>(CanalNotificacion.class);

    public NotificationDispatcher(List<NotificationSender> senders) {
        for (NotificationSender sender : senders) {
            this.senderMap.put(sender.getCanal(), sender);
        }
    }

    public boolean despachar(CanalNotificacion canal, String destinatario, String asunto, String mensaje) {
        NotificationSender sender = senderMap.get(canal);
        if (sender != null) {
            return sender.enviar(destinatario, asunto, mensaje);
        } else {
            log.warn("No se encontro un NotificationSender registrado para el canal: {}", canal);
            return false;
        }
    }

    public boolean soportaCanal(CanalNotificacion canal) {
        return senderMap.containsKey(canal);
    }
}
