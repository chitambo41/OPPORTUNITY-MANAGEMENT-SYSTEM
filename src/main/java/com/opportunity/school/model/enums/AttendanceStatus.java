package com.opportunity.school.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE;

    @JsonCreator
    public static AttendanceStatus fromString(String value) {
        if (value == null) return null;
        for (AttendanceStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown attendance status: " + value);
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
