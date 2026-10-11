package com.donatrack.incentivos.repository;

import com.donatrack.incentivos.domain.Insignia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InsigniaRepository extends JpaRepository<Insignia, Long> {
}
