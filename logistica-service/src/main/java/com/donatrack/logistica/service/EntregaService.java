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
    Entrega registrarArriboSede(String idEntrega, String observaciones);
    Entrega registrarArriboSedePorDonacion(Long idDonacion, String observaciones);
    Entrega replanificarEntrega(String idEntrega, ReplanificarRequestDto request);
    Entrega replanificarEntregaPorDonacion(Long idDonacion, ReplanificarRequestDto request);
    Entrega agregarEvidencias(String idEntrega, List<String> fotos);
    Entrega agregarEvidenciasPorDonacion(Long idDonacion, List<String> fotos);
    ComprobanteEntregaDto obtenerComprobante(String idEntrega);
    ComprobanteEntregaDto obtenerComprobantePorDonacion(Long idDonacion);
    Entrega obtenerPorDonacion(Long idDonacion);
    List<Entrega> listarEntregasPorRuta(String idRuta);
    List<Entrega> listarTodas();
    Entrega obtenerPorId(String idEntrega);
}
