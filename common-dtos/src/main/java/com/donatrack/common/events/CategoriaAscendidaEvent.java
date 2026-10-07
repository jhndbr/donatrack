package com.donatrack.common.events;

import java.time.LocalDateTime;

public class CategoriaAscendidaEvent {
    private Long idDonante;
    private String nombreDonante;
    private String emailDonante;
    private String categoriaPrevia;
    private String nuevaCategoria;
    private LocalDateTime timestamp;

    public CategoriaAscendidaEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public CategoriaAscendidaEvent(Long idDonante, String nombreDonante, String emailDonante,
                                   String categoriaPrevia, String nuevaCategoria) {
        this.idDonante = idDonante;
        this.nombreDonante = nombreDonante;
        this.emailDonante = emailDonante;
        this.categoriaPrevia = categoriaPrevia;
        this.nuevaCategoria = nuevaCategoria;
        this.timestamp = LocalDateTime.now();
    }

    public Long getIdDonante() { return idDonante; }
    public void setIdDonante(Long idDonante) { this.idDonante = idDonante; }

    public String getNombreDonante() { return nombreDonante; }
    public void setNombreDonante(String nombreDonante) { this.nombreDonante = nombreDonante; }

    public String getEmailDonante() { return emailDonante; }
    public void setEmailDonante(String emailDonante) { this.emailDonante = emailDonante; }

    public String getCategoriaPrevia() { return categoriaPrevia; }
    public void setCategoriaPrevia(String categoriaPrevia) { this.categoriaPrevia = categoriaPrevia; }

    public String getNuevaCategoria() { return nuevaCategoria; }
    public void setNuevaCategoria(String nuevaCategoria) { this.nuevaCategoria = nuevaCategoria; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
