package com.donatrack.logistica.repository;

import com.donatrack.logistica.domain.Camion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CamionRepository extends MongoRepository<Camion, String> {
    Optional<Camion> findByPatente(String patente);
    List<Camion> findByActivoTrue();
}
