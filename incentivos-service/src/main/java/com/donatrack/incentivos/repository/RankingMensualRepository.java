package com.donatrack.incentivos.repository;

import com.donatrack.incentivos.domain.RankingMensual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RankingMensualRepository extends JpaRepository<RankingMensual, Long> {
    List<RankingMensual> findByAnioAndMesOrderByPosicionAsc(int anio, int mes);
    List<RankingMensual> findTop3ByAnioAndMesOrderByPosicionAsc(int anio, int mes);
    void deleteByAnioAndMes(int anio, int mes);
}
