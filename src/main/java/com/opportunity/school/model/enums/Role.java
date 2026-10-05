package com.opportunity.school.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Role {
    ADMIN,
    TEACHER;

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null) return null;
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(value.trim())) {
                return r;
            }
        }
        return null;
    }
}
