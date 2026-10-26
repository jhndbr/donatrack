package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.Entrega;
import com.donatrack.logistica.dto.ComprobanteEntregaDto;
import com.donatrack.logistica.dto.ConfirmarEntregaRequestDto;
import com.donatrack.logistica.dto.EntregaFallidaRequestDto;
import com.donatrack.logistica.dto.ReplanificarRequestDto;

import java.util.List;

public interface EntregaService {
    Entrega confirmarEntrega(String idEntrega, ConfirmarEntregaRequestDto request);
    Entrega registrarEntregaFallida(String idEntrega, EntregaFallidaRequestDto request);
    Entrega confirmarEntregaPorDonacion(Long idDonacion, ConfirmarEntregaRequestDto request);
    Entrega registrarEntregaFallidaPorDonacion(Long idDonacion, EntregaFallidaRequestDto request);
    Entrega replanificarEntrega(String idEntrega, ReplanificarRequestDto request);
    ComprobanteEntregaDto obtenerComprobante(String idEntrega);
    List<Entrega> listarEntregasPorRuta(String idRuta);
    Entrega obtenerPorId(String idEntrega);
}
