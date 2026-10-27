package com.donatrack.logistica.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public record ConfirmarEntregaRequestDto(
        @NotEmpty(message = "Es obligatorio adjuntar al menos una fotografía de los bienes recibidos (CU-43)")
        List<String> fotos,

        String receptorNombre,
        String receptorDni,
        String firmaDigitalUrl,
        Long idEntidadBeneficiaria
) {
    public ConfirmarEntregaRequestDto(List<String> fotos, String receptorNombre, String receptorDni, String firmaDigitalUrl) {
        this(fotos, receptorNombre, receptorDni, firmaDigitalUrl, null);
    }

    @JsonCreator
    public static ConfirmarEntregaRequestDto create(
            @JsonProperty("fotos") List<String> fotos,
            @JsonProperty("urlFotoComprobante") String urlFotoComprobante,
            @JsonProperty("fotoUrl") String fotoUrl,
            @JsonProperty("urlFoto") String urlFoto,
            @JsonProperty("receptorNombre") String receptorNombre,
            @JsonProperty("receptorDni") String receptorDni,
            @JsonProperty("firmaDigitalUrl") String firmaDigitalUrl,
            @JsonProperty("idEntidadBeneficiaria") Long idEntidadBeneficiaria,
            @JsonProperty("entidadId") Long entidadId
    ) {
        List<String> lista = (fotos != null) ? new ArrayList<>(fotos) : new ArrayList<>();
        if (lista.isEmpty()) {
            if (urlFotoComprobante != null && !urlFotoComprobante.isBlank()) {
                lista.add(urlFotoComprobante.trim());
            } else if (fotoUrl != null && !fotoUrl.isBlank()) {
                lista.add(fotoUrl.trim());
            } else if (urlFoto != null && !urlFoto.isBlank()) {
                lista.add(urlFoto.trim());
            }
        }
        Long resolvedEntidadId = (idEntidadBeneficiaria != null) ? idEntidadBeneficiaria : entidadId;
        return new ConfirmarEntregaRequestDto(lista, receptorNombre, receptorDni, firmaDigitalUrl, resolvedEntidadId);
    }
}
