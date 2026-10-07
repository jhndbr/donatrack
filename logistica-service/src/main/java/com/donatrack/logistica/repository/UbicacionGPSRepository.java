package com.donatrack.logistica.repository;

import com.donatrack.logistica.domain.UbicacionGPS;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UbicacionGPSRepository extends MongoRepository<UbicacionGPS, String> {
    Optional<UbicacionGPS> findFirstByIdCamionOrderByTimestampDesc(String idCamion);
    Optional<UbicacionGPS> findFirstByPatenteOrderByTimestampDesc(String patente);
}
