package com.donatrack.notificaciones.template;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Generador de plantillas HTML institucionales para correos de DonaTrack (TODO-NOT-01).
 * Aplica diseño profesional unificado, tarjetas de datos, botones de acción (CTA) y footer legal.
 */
@Service
public class NotificationTemplateService {

    public String generarPlantillaHtml(String titulo,
                                       String saludo,
                                       String mensajePrincipal,
                                       Map<String, String> detalles,
                                       String botonTexto,
                                       String botonUrl) {

        StringBuilder filasDetalles = new StringBuilder();
        if (detalles != null && !detalles.isEmpty()) {
            filasDetalles.append("<table style='width: 100%; border-collapse: collapse; margin-top: 16px; margin-bottom: 20px; background-color: #f8fafc; border-radius: 8px; overflow: hidden; border: 1px solid #e2e8f0;'>");
            for (Map.Entry<String, String> entry : detalles.entrySet()) {
                filasDetalles.append("<tr>")
                        .append("<td style='padding: 10px 14px; font-weight: 600; color: #475569; width: 35%; border-bottom: 1px solid #e2e8f0;'>")
                        .append(escaparHtml(entry.getKey()))
                        .append("</td>")
                        .append("<td style='padding: 10px 14px; color: #0f172a; border-bottom: 1px solid #e2e8f0;'>")
                        .append(escaparHtml(entry.getValue()))
                        .append("</td>")
                        .append("</tr>");
            }
            filasDetalles.append("</table>");
        }

        StringBuilder botonHtml = new StringBuilder();
        if (botonTexto != null && !botonTexto.isBlank() && botonUrl != null && !botonUrl.isBlank()) {
            botonHtml.append("<div style='text-align: center; margin: 28px 0;'>")
                    .append("<a href='").append(escaparHtml(botonUrl)).append("' ")
                    .append("style='background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%); color: #ffffff; padding: 12px 28px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);'>")
                    .append(escaparHtml(botonTexto))
                    .append("</a>")
                    .append("</div>");
        }

        String template = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>{{TITULO}}</title>
                </head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #ffffff; margin: 0; padding: 0; color: #1e293b;">
                  <div style="max-width: 620px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e2e8f0;">
                    <!-- Header -->
                    <div style="background: linear-gradient(135deg, #0284c7 0%, #0f172a 100%); padding: 32px 24px; text-align: center;">
                      <h1 style="color: #ffffff; margin: 0; font-size: 26px; letter-spacing: -0.5px; font-weight: 800;">
                        💙 DonaTrack
                      </h1>
                      <p style="color: #93c5fd; margin: 6px 0 0 0; font-size: 14px; font-weight: 500;">
                        Trazabilidad y Gestión Solidaria de Donaciones
                      </p>
                    </div>
                
                    <!-- Contenido Principal -->
                    <div style="padding: 32px 28px;">
                      <h2 style="color: #0f172a; margin-top: 0; font-size: 20px; font-weight: 700;">{{TITULO}}</h2>
                      <p style="font-size: 15px; line-height: 1.6; color: #334155;">{{SALUDO}}</p>
                      <p style="font-size: 15px; line-height: 1.6; color: #475569;">{{MENSAJE}}</p>
                
                      <!-- Tarjeta de Detalles -->
                      {{DETALLES}}
                
                      <!-- Boton CTA -->
                      {{BOTON}}
                    </div>
                
                    <!-- Footer -->
                    <div style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 20px; text-align: center; font-size: 12px; color: #64748b;">
                      <p style="margin: 0 0 6px 0; font-weight: 600; color: #475569;">
                        DonaTrack · Universidad Tecnológica Nacional (UTN.BA)
                      </p>
                      <p style="margin: 0; line-height: 1.4;">
                        Este es un mensaje automático de la red de donaciones solidarias.<br>
                        Por favor, no respondas directamente a este correo.
                      </p>
                    </div>
                  </div>
                </body>
                </html>
                """;

        return template
                .replace("{{TITULO}}", escaparHtml(titulo))
                .replace("{{SALUDO}}", escaparHtml(saludo))
                .replace("{{MENSAJE}}", escaparHtml(mensajePrincipal))
                .replace("{{DETALLES}}", filasDetalles.toString())
                .replace("{{BOTON}}", botonHtml.toString());
    }

    public String generarTextoPlano(String titulo,
                                    String saludo,
                                    String mensajePrincipal,
                                    Map<String, String> detalles,
                                    String botonUrl) {
        StringBuilder sb = new StringBuilder();
        if (titulo != null && !titulo.isBlank()) {
            sb.append("[").append(titulo).append("]\n\n");
        }
        if (saludo != null && !saludo.isBlank()) {
            sb.append(saludo).append("\n\n");
        }
        if (mensajePrincipal != null && !mensajePrincipal.isBlank()) {
            sb.append(mensajePrincipal).append("\n");
        }
        if (detalles != null && !detalles.isEmpty()) {
            sb.append("\n");
            for (Map.Entry<String, String> entry : detalles.entrySet()) {
                sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        if (botonUrl != null && !botonUrl.isBlank()) {
            sb.append("\nMás info: ").append(botonUrl).append("\n");
        }
        return sb.toString().trim();
    }

    private String escaparHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br>");
    }
}
