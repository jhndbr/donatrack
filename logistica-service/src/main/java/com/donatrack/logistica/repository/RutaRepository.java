package com.donatrack.logistica.repository;

import com.donatrack.logistica.domain.Ruta;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RutaRepository extends MongoRepository<Ruta, String> {
    List<Ruta> findByFechaPlanificada(LocalDate fecha);
    List<Ruta> findByEstado(Ruta.EstadoRuta estado);
    List<Ruta> findByIdCamion(String idCamion);
}
