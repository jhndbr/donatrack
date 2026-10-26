package com.donatrack.logistica.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Custodia en MongoDB de donaciones que no pudieron ser asignadas a una ruta
 * por el planificador externo VRP, para reincorporación en la siguiente corrida nocturna (CU-38 A3).
 */
@Document(collection = "donaciones_pendientes_lote")
public class DonacionPendienteLote {

    @Id
    private String id;

    @Indexed
    private String loteId;

    @Indexed
    private Long idDonacion;

    private String motivoNoAsignacion;
    private LocalDateTime fechaRegistro = LocalDateTime.now();
    private boolean procesado = false;

    public DonacionPendienteLote() {
        this.fechaRegistro = LocalDateTime.now();
    }

    public DonacionPendienteLote(String loteId, Long idDonacion, String motivoNoAsignacion) {
        this();
        this.loteId = loteId;
        this.idDonacion = idDonacion;
        this.motivoNoAsignacion = motivoNoAsignacion;
        this.procesado = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLoteId() { return loteId; }
    public void setLoteId(String loteId) { this.loteId = loteId; }

    public Long getIdDonacion() { return idDonacion; }
    public void setIdDonacion(Long idDonacion) { this.idDonacion = idDonacion; }

    public String getMotivoNoAsignacion() { return motivoNoAsignacion; }
    public void setMotivoNoAsignacion(String motivoNoAsignacion) { this.motivoNoAsignacion = motivoNoAsignacion; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public boolean isProcesado() { return procesado; }
    public void setProcesado(boolean procesado) { this.procesado = procesado; }
}
