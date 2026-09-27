package com.cph.alpharoute.data.repository;

/**
 * How a repository write ended.
 *
 * <p>Writes are expected to fail in ordinary, non-exceptional ways - a duplicate store code, an edit
 * to a store that was deleted a moment ago - so they are reported as values rather than thrown as
 * exceptions. Only a genuinely unexpected failure maps to {@link #FAILED}, and that path logs the
 * underlying exception.
 *
 * <p>These names are also what the UI turns into messages: the Phase 0.3 screen plan lists exactly
 * these cases as the inline field errors on the Add/Edit Store sheet.
 */
public enum WriteOutcome {

    /** The write committed. */
    SUCCESS,

    /** Another store already uses this code. Enforced by a UNIQUE index, checked first for a clear message. */
    DUPLICATE_STORE_CODE,

    /** Another store already uses this route number. Enforced by a UNIQUE index. */
    DUPLICATE_ROUTE_NUMBER,

    /** The target row no longer exists - it was deleted between the screen loading and the write. */
    NOT_FOUND,

    /**
     * The data failed a rule the repository enforces itself: a coordinate outside the globe, or text
     * that is blank after trimming.
     *
     * <p>This is the repository's own backstop, not the form's validation. The screen runs
     * {@code StoreValidator} first and reports per-field errors; this outcome only appears if a caller
     * bypassed that, which means a bug rather than a user mistake.
     */
    INVALID_DATA,

    /**
     * The write was refused because completion history references the row.
     *
     * <p>Architecture decision AD-6: the foreign key uses {@code onDelete = RESTRICT}, so the database
     * itself rejects the delete. Completed stores and their history cannot be removed by accident.
     */
    PROTECTED_BY_HISTORY,

    /** Something unexpected went wrong. The cause is logged. */
    FAILED
}
