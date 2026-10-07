package com.donatrack.common.enums;

public enum CategoriaDonante {
    COLABORADOR(1),
    SOSTENEDOR(2),
    TRANSFORMADOR(3);

    private final int nivel;

    CategoriaDonante(int nivel) {
        this.nivel = nivel;
    }

    public int getNivel() {
        return nivel;
    }
}
