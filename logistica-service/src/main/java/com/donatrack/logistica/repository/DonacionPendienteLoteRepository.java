package com.donatrack.logistica.repository;

import com.donatrack.logistica.domain.DonacionPendienteLote;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DonacionPendienteLoteRepository extends MongoRepository<DonacionPendienteLote, String> {
    List<DonacionPendienteLote> findByProcesadoFalse();
    List<DonacionPendienteLote> findByLoteId(String loteId);
}
