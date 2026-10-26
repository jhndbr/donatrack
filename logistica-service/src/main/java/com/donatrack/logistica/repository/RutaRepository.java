package com.donatrack.logistica.repository;

import com.donatrack.logistica.domain.Ruta;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RutaRepository extends MongoRepository<Ruta, String> {
    List<Ruta> findByFechaPlanificada(LocalDate fecha);
    List<Ruta> findByEstado(Ruta.EstadoRuta estado);
    List<Ruta> findByIdCamion(String idCamion);
    List<Ruta> findByIdCamionAndEstadoIn(String idCamion, List<Ruta.EstadoRuta> estados);
    boolean existsByIdCamionAndEstadoIn(String idCamion, List<Ruta.EstadoRuta> estados);
    List<Ruta> findByLoteId(String loteId);
}
