package com.opportunity.school.model.enums;

public enum StudentStatus {
    ACTIVE,
    COMPLETE,
    REMOVED;

    /** Alias used in some service code paths. */
    public static StudentStatus completed() {
        return COMPLETE;
    }
}
