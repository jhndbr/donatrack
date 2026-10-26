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
    void testProgresoTipoHabilDonadorExigeUmbralEnDonacionUnica() {
        Mision misionHabil = new Mision("Gran Donador", "Donar 50 bienes en una sola entrega",
                Mision.TipoMision.HABIL_DONADOR, CategoriaDonante.COLABORADOR, 50.0, 1, "Insignia Generoso");
        misionHabil.setId(30L);
        ProgresoMision progresoHabil = new ProgresoMision(42L, misionHabil);

        when(perfilRepository.findByIdDonante(42L)).thenReturn(Optional.of(perfil));
        when(progresoRepository.findByIdDonante(42L)).thenReturn(List.of(progresoHabil));

        // Impacto con 30 bienes (< 50): no debe acumular donaciones menores
        incentivosService.registrarImpactoDonacion(42L, "Lucía Benítez", "lucia@mail.com", 30.0);

        assertEquals(0.0, progresoHabil.getProgresoActual());
        assertFalse(progresoHabil.isCompletada());

        // Impacto con 50 bienes (>= 50): cumple el umbral de una sola vez
        when(misionRepository.findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(CategoriaDonante.COLABORADOR)).thenReturn(List.of(misionHabil));
        when(progresoRepository.findByIdDonanteAndCompletadaTrue(42L)).thenReturn(List.of(progresoHabil));
        incentivosService.registrarImpactoDonacion(42L, "Lucía Benítez", "lucia@mail.com", 50.0);

        assertEquals(50.0, progresoHabil.getProgresoActual());
        assertTrue(progresoHabil.isCompletada());
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

    @Test
    void testMisionCompletitudSumaProgresoConNuevaSubcategoria() {
        Mision misionComp = new Mision("Variedad Solidaria", "Donar 2 subcategorías distintas",
                Mision.TipoMision.COMPLETITUD, CategoriaDonante.COLABORADOR, 2.0, 1, "Insignia Multifacético");
        misionComp.setId(40L);
        ProgresoMision progresoComp = new ProgresoMision(42L, misionComp);

        when(perfilRepository.findByIdDonante(42L)).thenReturn(Optional.of(perfil));
        when(progresoRepository.findByIdDonante(42L)).thenReturn(List.of(progresoComp));

        // Primera subcategoría "alimentos"
        incentivosService.registrarImpactoDonacionCompleto(42L, "Lucía", "lucia@mail.com", 1.0, "alimentos", 1L);
        assertEquals(1.0, progresoComp.getProgresoActual());
        assertFalse(progresoComp.isCompletada());

        // Segunda donación con misma subcategoría "alimentos" -> no debe sumar progreso
        incentivosService.registrarImpactoDonacionCompleto(42L, "Lucía", "lucia@mail.com", 1.0, "alimentos", 1L);
        assertEquals(1.0, progresoComp.getProgresoActual());

        // Tercera donación con subcategoría nueva "ropa" -> debe sumar y completar
        when(misionRepository.findByCategoriaRequeridaOrderByOrdenSecuenciaAsc(CategoriaDonante.COLABORADOR)).thenReturn(List.of(misionComp));
        when(progresoRepository.findByIdDonanteAndCompletadaTrue(42L)).thenReturn(List.of(progresoComp));

        incentivosService.registrarImpactoDonacionCompleto(42L, "Lucía", "lucia@mail.com", 1.0, "ropa", 1L);
        assertEquals(2.0, progresoComp.getProgresoActual());
        assertTrue(progresoComp.isCompletada());
    }

    @Test
    void testRachaMensualIncrementoYReseteoPorInactividad() {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        java.time.LocalDate haceTresMeses = hoy.minusMonths(3);

        perfil.setUltimaDonacionFecha(haceTresMeses);
        perfil.setRachaMesesConsecutivos(5);

        // Donación hoy tras inactividad: debe reiniciar la racha a 1
        perfil.actualizarRachaMensual(hoy);
        assertEquals(1, perfil.getRachaMesesConsecutivos());
        assertEquals(hoy, perfil.getUltimaDonacionFecha());

        // Donación en el mes siguiente consecutivo: racha debe incrementar a 2
        java.time.LocalDate proximoMes = hoy.plusMonths(1);
        perfil.actualizarRachaMensual(proximoMes);
        assertEquals(2, perfil.getRachaMesesConsecutivos());
    }

    @Test
    void testSecuencialidadEstrictaNoEvaluaSegundaMisionSiPrimeraNoAvanza() {
        Mision misionComp = new Mision("Variedad Solidaria", "Donar 2 subcategorías distintas",
                Mision.TipoMision.COMPLETITUD, CategoriaDonante.COLABORADOR, 2.0, 1, "Insignia 1");
        misionComp.setId(40L);
        ProgresoMision prog1 = new ProgresoMision(42L, misionComp);
        prog1.setProgresoActual(1.0);
        perfil.registrarSubcategoria("alimentos");

        Mision misionHabil = new Mision("Gran Donador", "Donar 10 bienes de una vez",
                Mision.TipoMision.HABIL_DONADOR, CategoriaDonante.COLABORADOR, 10.0, 2, "Insignia 2");
        misionHabil.setId(41L);
        ProgresoMision prog2 = new ProgresoMision(42L, misionHabil);

        when(perfilRepository.findByIdDonante(42L)).thenReturn(Optional.of(perfil));
        when(progresoRepository.findByIdDonante(42L)).thenReturn(List.of(prog1, prog2));

        // Repite subcategoría "alimentos" con 50 bienes: prog1 no avanza y prog2 NO debe evaluarse por secuencialidad
        incentivosService.registrarImpactoDonacionCompleto(42L, "Lucía", "lucia@mail.com", 50.0, "alimentos", null);

        assertEquals(1.0, prog1.getProgresoActual());
        assertFalse(prog1.isCompletada());
        assertEquals(0.0, prog2.getProgresoActual());
        assertFalse(prog2.isCompletada());
    }
}
