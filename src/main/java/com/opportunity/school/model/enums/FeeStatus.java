package com.opportunity.school.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FeeStatus {
    PAID,
    PARTIAL,
    NOT_PAID;

    @JsonCreator
    public static FeeStatus fromString(String value) {
        if (value == null) return null;
        for (FeeStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown fee status: " + value);
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
