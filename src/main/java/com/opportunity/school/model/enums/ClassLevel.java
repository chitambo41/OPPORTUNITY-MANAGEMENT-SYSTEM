package com.opportunity.school.model.enums;

/**
 * Fixed ascending class levels: BABY_CLASS -> KG1 -> KG2 -> KG3.
 */
public enum ClassLevel {
    BABY_CLASS,
    KG1,
    KG2,
    KG3;

    /**
     * @return the next level in the fixed order, or null when this is KG3 (students COMPLETE after KG3).
     */
    public ClassLevel next() {
        ClassLevel[] levels = values();
        int idx = ordinal();
        if (idx + 1 >= levels.length) {
            return null;
        }
        return levels[idx + 1];
    }
}
