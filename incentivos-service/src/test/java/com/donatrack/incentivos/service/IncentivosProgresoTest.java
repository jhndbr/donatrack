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

    @Test
    void testRegistrarEntregaExitosaAvanzaMisionDonacionesExitosas() {
        Mision misionEntregas = new Mision("Entrega Concluida", "1 entrega finalizada",
                Mision.TipoMision.DONACIONES_EXITOSAS, CategoriaDonante.COLABORADOR, 1.0, 2, "Insignia Confiable");
        misionEntregas.setId(20L);
        ProgresoMision progresoEntregas = new ProgresoMision(42L, misionEntregas);

        when(perfilRepository.findByIdDonante(42L)).thenReturn(Optional.of(perfil));
        when(progresoRepository.findByIdDonante(42L)).thenReturn(List.of(progresoEntregas));
        when(misionRepository.findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(CategoriaDonante.COLABORADOR)).thenReturn(List.of(misionEntregas));
        when(progresoRepository.findByIdDonanteAndCompletadaTrue(42L)).thenReturn(List.of(progresoEntregas));

        incentivosService.registrarEntregaExitosa(42L, "lucia@mail.com");

        assertTrue(progresoEntregas.isCompletada());
        assertEquals(1, perfil.getInsignias().size());
        assertEquals("Insignia Confiable", perfil.getInsignias().get(0).getNombre());
    }

    @Test
    void testRegistrarEntregaExitosaPorEmail() {
        when(perfilRepository.findByEmail("lucia@mail.com")).thenReturn(Optional.of(perfil));
        when(perfilRepository.findByIdDonante(42L)).thenReturn(Optional.of(perfil));
        when(progresoRepository.findByIdDonante(42L)).thenReturn(List.of(progreso1));

        incentivosService.registrarEntregaExitosaPorEmail("lucia@mail.com");

        verify(perfilRepository, atLeastOnce()).findByEmail("lucia@mail.com");
    }

    @Test
    void testProgresoTipoHabilDonadorSumaCantidadDeBienes() {
        Mision misionHabil = new Mision("Gran Donador", "Donar 50 bienes en total",
                Mision.TipoMision.HABIL_DONADOR, CategoriaDonante.COLABORADOR, 50.0, 1, "Insignia Generoso");
        misionHabil.setId(30L);
        ProgresoMision progresoHabil = new ProgresoMision(42L, misionHabil);

        when(perfilRepository.findByIdDonante(42L)).thenReturn(Optional.of(perfil));
        when(progresoRepository.findByIdDonante(42L)).thenReturn(List.of(progresoHabil));

        // Impacto con 30 bienes
        incentivosService.registrarImpactoDonacion(42L, "Lucía Benítez", "lucia@mail.com", 30.0);

        assertEquals(30.0, progresoHabil.getProgresoActual());
        assertFalse(progresoHabil.isCompletada());
    }

    @Test
    void testObtenerOCrearPerfilInexistente() {
        when(perfilRepository.findByIdDonante(99L)).thenReturn(Optional.empty());
        when(misionRepository.findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(CategoriaDonante.COLABORADOR)).thenReturn(List.of(mision1));
        when(progresoRepository.findByIdDonanteAndMisionId(99L, 10L)).thenReturn(Optional.empty());

        PerfilDonanteIncentivo nuevo = incentivosService.obtenerOCrearPerfil(99L, "Nuevo Donante", "nuevo@mail.com");

        assertNotNull(nuevo);
        assertEquals(99L, nuevo.getIdDonante());
        assertEquals("Nuevo Donante", nuevo.getNombre());
        verify(perfilRepository, times(1)).save(nuevo);
        verify(progresoRepository, times(1)).save(any(ProgresoMision.class));
    }
}
