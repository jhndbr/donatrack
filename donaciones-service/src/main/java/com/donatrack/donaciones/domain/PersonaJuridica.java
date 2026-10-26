package com.donatrack.donaciones.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "personas_juridicas")
public class PersonaJuridica extends PersonaDonante {

    @Column(name = "razon_social", nullable = false)
    private String razonSocial;

    @Column(nullable = false, unique = true)
    private String cuit;

    private String tipo; // Gubernamental, ONG, Empresa, Institucion

    private String rubro;

    private String direccion;

    private Double latitud;

    private Double longitud;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "representantes_juridica", joinColumns = @JoinColumn(name = "persona_juridica_id"))
    @Column(name = "nombre_representante")
    private List<String> representantes = new ArrayList<>();

    public PersonaJuridica() {
        super();
    }

    public PersonaJuridica(String razonSocial, String cuit, String tipo, String rubro) {
        super();
        this.razonSocial = razonSocial;
        this.cuit = cuit;
        this.tipo = tipo;
        this.rubro = rubro;
    }

    @Override
    public String getNombreCompleto() {
        return razonSocial;
    }

    @Override
    public String getIdentificadorPrincipal() {
        return cuit;
    }

    @Override
    public String getEmailPrincipal() {
        return getMediosContacto().stream()
                .filter(m -> m.getTipo() == com.donatrack.common.enums.CanalNotificacion.EMAIL)
                .map(MedioContacto::getValor)
                .findFirst()
                .orElse(null);
    }

    public void agregarRepresentante(String representante) {
        this.representantes.add(representante);
    }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getCuit() { return cuit; }
    public void setCuit(String cuit) { this.cuit = cuit; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getRubro() { return rubro; }
    public void setRubro(String rubro) { this.rubro = rubro; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }

    public List<String> getRepresentantes() { return representantes; }
    public void setRepresentantes(List<String> representantes) { this.representantes = representantes; }
}
