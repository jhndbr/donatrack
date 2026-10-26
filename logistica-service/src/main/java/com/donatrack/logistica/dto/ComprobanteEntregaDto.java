package com.donatrack.logistica.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ComprobanteEntregaDto(
        String idEntrega,
        Long idDonacion,
        Long idEntidadBeneficiaria,
        String nombreEntidad,
        String patenteCamion,
        String receptorNombre,
        String receptorDni,
        String firmaDigitalUrl,
        List<String> fotosConfirmacionUrls,
        LocalDateTime fechaConfirmacion,
        String estado
) {}
