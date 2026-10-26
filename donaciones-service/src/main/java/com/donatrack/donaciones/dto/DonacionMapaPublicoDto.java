package com.donatrack.donaciones.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO optimizado para el endpoint público de visualización de donaciones entregadas en mapa (CU-03, CU-04).
 */
public record DonacionMapaPublicoDto(
    Long idDonacion,
    String subcategoria,
    double cantidad,
    String unidadMedida,
    boolean esUsado,
    String entidadBeneficiaria,
    String direccionEntrega,
    Double latitud,
    Double longitud,
    LocalDateTime fechaEntrega,
    List<String> fotosBienes
) {}
