package com.donatrack.donaciones.service;

import com.donatrack.common.enums.EstadoDonacion;
import com.donatrack.donaciones.domain.*;
import com.donatrack.donaciones.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DonacionServiceTest {

    @Mock
    private DonacionRepository donacionRepository;

    @Mock
    private PersonaDonanteRepository donanteRepository;

    @Mock
    private EntidadBeneficiariaRepository entidadRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DonacionService donacionService;

    private PersonaHumana donante;

    @BeforeEach
    void setUp() {
        donante = new PersonaHumana("Juan", "Perez", 35, "30111222", "M", "Av. Siempreviva 123");
    }

    @Test
    void testSegmentacionAutomaticaPorSubcategoria() {
        when(donanteRepository.findById(1L)).thenReturn(Optional.of(donante));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BienDonado bien1 = new BienDonado("Fideos Tallarines", "Alimentos", "Pastas Secas", 10.0, "paquetes", false, null, null);
        BienDonado bien2 = new BienDonado("Fideos Tirabuzón", "Alimentos", "Pastas Secas", 15.0, "paquetes", false, null, null);
        BienDonado bien3 = new BienDonado("Camperas polar", "Indumentaria", "Abrigo", 5.0, "unidades", false, null, null);

        List<Donacion> resultado = donacionService.registrarCargaSegmentada(1L, List.of(bien1, bien2, bien3));

        // Debe haber agrupado los fideos en 1 donación de Pastas Secas y las camperas en otra donación de Abrigo
        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().anyMatch(d -> d.getSubcategoria().equals("Pastas Secas") && d.getCantidad() == 25.0));
        assertTrue(resultado.stream().anyMatch(d -> d.getSubcategoria().equals("Abrigo") && d.getCantidad() == 5.0));
        verify(donacionRepository, times(2)).save(any(Donacion.class));
    }

    @Test
    void testActualizarEstadoLote() {
        Donacion d1 = new Donacion(donante, "Arroz", 10.0, "kg", false, null);
        d1.asignarA(new EntidadBeneficiaria("Entidad 1", "Dir 1", "123"));

        when(donacionRepository.findById(101L)).thenReturn(Optional.of(d1));
        when(donacionRepository.save(any(Donacion.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Donacion> actualizadas = donacionService.actualizarEstadoLote(
                List.of(101L),
                EstadoDonacion.LISTA_PARA_ENTREGAR,
                null
        );

        assertEquals(1, actualizadas.size());
        assertEquals(EstadoDonacion.LISTA_PARA_ENTREGAR, actualizadas.get(0).getEstado());
    }
}
