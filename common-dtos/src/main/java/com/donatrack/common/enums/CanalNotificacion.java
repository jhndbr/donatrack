package com.donatrack.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CanalNotificacion {
    EMAIL,
    SMS,
    WHATSAPP;

    @JsonCreator
    public static CanalNotificacion fromString(String val) {
        if (val == null || val.isBlank()) {
            return EMAIL;
        }
        String upper = val.trim().toUpperCase();
        if ("TELEFONO".equals(upper) || "PHONE".equals(upper) || "CELULAR".equals(upper)) {
            return SMS;
        }
        for (CanalNotificacion c : values()) {
            if (c.name().equals(upper)) {
                return c;
            }
        }
        return EMAIL;
    }
}
