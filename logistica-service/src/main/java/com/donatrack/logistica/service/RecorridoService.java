package com.donatrack.logistica.service;

import com.donatrack.logistica.domain.Ruta;

import java.time.LocalDate;
import java.util.List;

public interface RecorridoService {
    Ruta iniciarRuta(String idRuta);
    Ruta finalizarRuta(String idRuta);
    Ruta obtenerRutaPorId(String idRuta);
    List<Ruta> listarRutas(LocalDate fecha, Ruta.EstadoRuta estado);
}
