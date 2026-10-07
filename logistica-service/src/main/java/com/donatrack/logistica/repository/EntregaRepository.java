package com.donatrack.logistica.repository;

import com.donatrack.logistica.domain.Entrega;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntregaRepository extends MongoRepository<Entrega, String> {
    List<Entrega> findByIdRuta(String idRuta);
    List<Entrega> findByEstado(Entrega.EstadoEntrega estado);
    Optional<Entrega> findByIdDonacion(Long idDonacion);
}
