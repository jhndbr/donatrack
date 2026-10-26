package com.donatrack.frontend.security;

import java.io.Serializable;

public class UserSession implements Serializable {

    private String username;
    private String nombreCompleto;
    private String email;
    private String role; // "ROLE_ADMIN", "ROLE_DONANTE", "ROLE_BENEFICIARIA"
    private Long entidadId;
    private Long donanteId;
    private String accessToken;

    public UserSession() {}

    public UserSession(String username, String nombreCompleto, String email, String role) {
        this.username = username;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.role = role;
    }

    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }

    public boolean isDonante() {
        return "ROLE_DONANTE".equals(role);
    }

    public boolean isBeneficiaria() {
        return "ROLE_BENEFICIARIA".equals(role);
    }

    public boolean isChofer() {
        return "ROLE_CHOFER".equals(role);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getNombre() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public void setEntidadId(Long entidadId) {
        this.entidadId = entidadId;
    }

    public Long getDonanteId() {
        return donanteId;
    }

    public void setDonanteId(Long donanteId) {
        this.donanteId = donanteId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
