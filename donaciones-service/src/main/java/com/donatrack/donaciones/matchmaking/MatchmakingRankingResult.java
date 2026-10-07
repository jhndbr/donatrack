package com.donatrack.donaciones.matchmaking;

import com.donatrack.donaciones.domain.NecesidadMaterial;
import java.util.List;

public class MatchmakingRankingResult {
    public static class EvaluacionPar {
        private Long idNecesidad;
        private Long idEntidad;
        private String nombreEntidad;
        private String descripcionNecesidad;
        private double score;

        public EvaluacionPar(NecesidadMaterial necesidad, double score) {
            this.idNecesidad = necesidad.getId();
            this.idEntidad = necesidad.getEntidadBeneficiaria() != null ? necesidad.getEntidadBeneficiaria().getId() : null;
            this.nombreEntidad = necesidad.getEntidadBeneficiaria() != null ? necesidad.getEntidadBeneficiaria().getRazonSocial() : "";
            this.descripcionNecesidad = necesidad.getDescripcion();
            this.score = score;
        }

        public Long getIdNecesidad() { return idNecesidad; }
        public Long getIdEntidad() { return idEntidad; }
        public String getNombreEntidad() { return nombreEntidad; }
        public String getDescripcionNecesidad() { return descripcionNecesidad; }
        public double getScore() { return score; }
    }

    private Long idDonacion;
    private String subcategoria;
    private List<EvaluacionPar> rankingSemanticoTop10;
    private List<EvaluacionPar> rankingSubatendidosTop10;
    private List<EvaluacionPar> coincidenciasDestacadas; // Aparecen en ambos rankings

    public MatchmakingRankingResult(Long idDonacion, String subcategoria,
                                    List<EvaluacionPar> rankingSemanticoTop10,
                                    List<EvaluacionPar> rankingSubatendidosTop10,
                                    List<EvaluacionPar> coincidenciasDestacadas) {
        this.idDonacion = idDonacion;
        this.subcategoria = subcategoria;
        this.rankingSemanticoTop10 = rankingSemanticoTop10;
        this.rankingSubatendidosTop10 = rankingSubatendidosTop10;
        this.coincidenciasDestacadas = coincidenciasDestacadas;
    }

    public Long getIdDonacion() { return idDonacion; }
    public String getSubcategoria() { return subcategoria; }
    public List<EvaluacionPar> getRankingSemanticoTop10() { return rankingSemanticoTop10; }
    public List<EvaluacionPar> getRankingSubatendidosTop10() { return rankingSubatendidosTop10; }
    public List<EvaluacionPar> getCoincidenciasDestacadas() { return coincidenciasDestacadas; }
}
