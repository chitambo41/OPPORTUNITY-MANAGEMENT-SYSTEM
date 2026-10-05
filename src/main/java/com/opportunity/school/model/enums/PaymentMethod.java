package com.opportunity.school.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {
    CASH,
    BANK,
    MOBILE_MONEY;

    @JsonCreator
    public static PaymentMethod fromString(String value) {
        if (value == null) return null;
        for (PaymentMethod m : values()) {
            if (m.name().equalsIgnoreCase(value.trim())) {
                return m;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + value);
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
