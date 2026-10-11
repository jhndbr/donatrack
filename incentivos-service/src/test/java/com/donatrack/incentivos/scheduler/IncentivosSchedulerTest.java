package com.donatrack.incentivos.scheduler;

import com.donatrack.incentivos.domain.PerfilDonanteIncentivo;
import com.donatrack.incentivos.domain.ProgresoMision;
import com.donatrack.incentivos.domain.RankingMensual;
import com.donatrack.incentivos.repository.PerfilDonanteIncentivoRepository;
import com.donatrack.incentivos.repository.ProgresoMisionRepository;
import com.donatrack.incentivos.repository.RankingMensualRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IncentivosSchedulerTest {

    @Mock
    private PerfilDonanteIncentivoRepository perfilRepository;

    @Mock
    private ProgresoMisionRepository progresoRepository;

    @Mock
    private RankingMensualRepository rankingRepository;

    @InjectMocks
    private IncentivosScheduler scheduler;

    @Test
    void testEjecutarCalculoRankingMensual() {
        PerfilDonanteIncentivo p1 = new PerfilDonanteIncentivo(1L, "Donante 1", "d1@mail.com");
        PerfilDonanteIncentivo p2 = new PerfilDonanteIncentivo(2L, "Donante 2", "d2@mail.com");

        when(perfilRepository.findAll()).thenReturn(List.of(p1, p2));

        // p1 completó 3 misiones, p2 completó 1 misión
        ProgresoMision prog = mock(ProgresoMision.class);
        when(progresoRepository.findByIdDonanteAndCompletadaTrue(1L)).thenReturn(List.of(prog, prog, prog));
        when(progresoRepository.findByIdDonanteAndCompletadaTrue(2L)).thenReturn(List.of(prog));

        scheduler.ejecutarCalculoRankingMensual();

        ArgumentCaptor<RankingMensual> captor = ArgumentCaptor.forClass(RankingMensual.class);
        verify(rankingRepository, times(2)).save(captor.capture());

        List<RankingMensual> guardados = captor.getAllValues();
        // Primer lugar: p1 con 3 misiones
        assertEquals(1L, guardados.get(0).getIdDonante());
        assertEquals(1, guardados.get(0).getPosicion());
        assertEquals(3, guardados.get(0).getMisionesCompletadasMes());

        // Segundo lugar: p2 con 1 misión
        assertEquals(2L, guardados.get(1).getIdDonante());
        assertEquals(2, guardados.get(1).getPosicion());
        assertEquals(1, guardados.get(1).getMisionesCompletadasMes());
    }
}
