package com.opportunity.school.model.enums;

public enum RemovalReason {
    SHIFT,
    DIED,
    FIRED,
    COMPLETE;

    /** @return true when this reason is used for student removals. */
    public boolean isStudentReason() {
        return this != FIRED;
    }
}
