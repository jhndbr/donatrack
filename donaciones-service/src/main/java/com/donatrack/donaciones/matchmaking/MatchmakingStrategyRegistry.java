package com.donatrack.donaciones.matchmaking;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro central de estrategias de asignación algorítmica (Patrón Registry + OCP).
 * Auto-descubre todas las implementaciones de MatchmakingStrategy disponibles en el contenedor
 * y permite registrar dinámicamente nuevas métricas futuras (ej. cercanía geográfica)
 * sin modificar la lógica interna de MatchmakingService.
 */
@Component
public class MatchmakingStrategyRegistry {

    private final Map<String, MatchmakingStrategy> strategiesByName = new ConcurrentHashMap<>();

    public MatchmakingStrategyRegistry() {}

    @org.springframework.beans.factory.annotation.Autowired
    public MatchmakingStrategyRegistry(List<MatchmakingStrategy> strategies) {
        if (strategies != null) {
            strategies.forEach(this::registrar);
        }
    }

    public void registrar(MatchmakingStrategy strategy) {
        if (strategy != null) {
            String nombre = (strategy.getNombre() != null && !strategy.getNombre().isBlank())
                    ? strategy.getNombre()
                    : strategy.getClass().getSimpleName();
            strategiesByName.put(nombre.toLowerCase().trim(), strategy);
        }
    }

    public Optional<MatchmakingStrategy> obtenerPorNombre(String nombre) {
        if (nombre == null) return Optional.empty();
        return Optional.ofNullable(strategiesByName.get(nombre.toLowerCase().trim()));
    }

    public <T extends MatchmakingStrategy> Optional<T> obtenerPorTipo(Class<T> strategyClass) {
        return strategiesByName.values().stream()
                .filter(strategyClass::isInstance)
                .map(strategyClass::cast)
                .findFirst();
    }

    public Collection<MatchmakingStrategy> listarEstrategias() {
        return Collections.unmodifiableCollection(strategiesByName.values());
    }
}
