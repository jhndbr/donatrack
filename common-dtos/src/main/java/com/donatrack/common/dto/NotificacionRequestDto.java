package com.donatrack.common.dto;

import com.donatrack.common.enums.CanalNotificacion;

public class NotificacionRequestDto {
    private String destinatario;
    private CanalNotificacion canal;
    private String asunto;
    private String cuerpo;

    public NotificacionRequestDto() {}

    public NotificacionRequestDto(String destinatario, CanalNotificacion canal, String asunto, String cuerpo) {
        this.destinatario = destinatario;
        this.canal = canal;
        this.asunto = asunto;
        this.cuerpo = cuerpo;
    }

    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }

    public CanalNotificacion getCanal() { return canal; }
    public void setCanal(CanalNotificacion canal) { this.canal = canal; }

    public String getAsunto() { return asunto; }
    public void setAsunto(String asunto) { this.asunto = asunto; }

    public String getCuerpo() { return cuerpo; }
    public void setCuerpo(String cuerpo) { this.cuerpo = cuerpo; }
}
