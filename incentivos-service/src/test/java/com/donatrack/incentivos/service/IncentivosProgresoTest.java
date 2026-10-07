package com.donatrack.incentivos.service;

import com.donatrack.common.enums.CategoriaDonante;
import com.donatrack.incentivos.domain.*;
import com.donatrack.incentivos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IncentivosProgresoTest {

    @Mock
    private PerfilDonanteIncentivoRepository perfilRepository;

    @Mock
    private MisionRepository misionRepository;

    @Mock
    private ProgresoMisionRepository progresoRepository;

    @Mock
    private RankingMensualRepository rankingRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private IncentivosService incentivosService;

    private PerfilDonanteIncentivo perfil;
    private Mision mision1;
    private ProgresoMision progreso1;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(incentivosService, "exchange", "donatrack.events.exchange");
        ReflectionTestUtils.setField(incentivosService, "routingKeyMisionCumplida", "mision.cumplida");
        ReflectionTestUtils.setField(incentivosService, "routingKeyCategoriaAscendida", "categoria.ascendida");
        ReflectionTestUtils.setField(incentivosService, "n8nWebhookUrl", "http://localhost:5678/webhook/insignias");

        perfil = new PerfilDonanteIncentivo(42L, "Lucía Benítez", "lucia@mail.com");
        mision1 = new Mision("Primer Paso", "Realizar 1 donación", Mision.TipoMision.RACHA, CategoriaDonante.COLABORADOR, 1.0, 1, "Insignia Inicial");
        mision1.setId(10L);
        progreso1 = new ProgresoMision(42L, mision1);
    }

    @Test
    void testCumplimientoMisionDesbloqueaInsigniaYEmiteEvento() {
        when(perfilRepository.findByIdDonante(42L)).thenReturn(Optional.of(perfil));
        when(progresoRepository.findByIdDonante(42L)).thenReturn(List.of(progreso1));
        when(misionRepository.findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(CategoriaDonante.COLABORADOR)).thenReturn(List.of(mision1));
        when(progresoRepository.findByIdDonanteAndCompletadaTrue(42L)).thenReturn(List.of(progreso1));

        incentivosService.registrarImpactoDonacion(42L, "Lucía Benítez", "lucia@mail.com", 15.0);

        assertTrue(progreso1.isCompletada());
        assertEquals(1, perfil.getInsignias().size());
        assertEquals("Insignia Inicial", perfil.getInsignias().get(0).getNombre());

        verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void testAscensoDeCategoriaAlCompletarTodasLasMisiones() {
        when(perfilRepository.findByIdDonante(42L)).thenReturn(Optional.of(perfil));
        when(progresoRepository.findByIdDonante(42L)).thenReturn(List.of(progreso1));
        when(misionRepository.findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(CategoriaDonante.COLABORADOR)).thenReturn(List.of(mision1));
        when(progresoRepository.findByIdDonanteAndCompletadaTrue(42L)).thenReturn(List.of(progreso1));

        incentivosService.registrarImpactoDonacion(42L, "Lucía Benítez", "lucia@mail.com", 15.0);

        // Al completar la única misión de COLABORADOR, asciende a SOSTENEDOR
        assertEquals(CategoriaDonante.SOSTENEDOR, perfil.getCategoria());
    }
}
