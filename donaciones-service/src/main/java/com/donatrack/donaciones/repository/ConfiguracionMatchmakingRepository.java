package com.donatrack.donaciones.repository;

import com.donatrack.donaciones.domain.ConfiguracionMatchmaking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionMatchmakingRepository extends JpaRepository<ConfiguracionMatchmaking, Long> {
}
