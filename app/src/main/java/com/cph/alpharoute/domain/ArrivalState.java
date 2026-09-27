package com.cph.alpharoute.domain;

/**
 * How close the driver is to the store they are navigating to.
 *
 * <p>Four outcomes, and every one of them is a real situation rather than a variation on another. The
 * point of an enum here rather than a boolean is that "arrived" and "cannot tell" must not be the same
 * value: a boolean forces them together, and the collapse always resolves in the dangerous direction -
 * a failure to read the GPS would come out as "yes, arrived", which is the one answer this app must
 * never give wrongly.
 *
 * <p>Pure Java, zero Android imports (AD-11).
 */
public enum ArrivalState {

    /**
     * Nothing is being navigated to, so there is nothing to be close to or far from.
     *
     * <p>Distinct from {@link #OUTSIDE}: "you are 4 km away" is information, and "there is no destination"
     * is not.
     */
    NO_DESTINATION,

    /** A destination is known, but no usable position reading has arrived yet. */
    NO_FIX,

    /** The driver is comfortably outside the arrival radius. */
    OUTSIDE,

    /**
     * The driver is outside the arrival radius, but close to its edge.
     *
     * <p>This exists because a phone's GPS is not exact. A fix is a point with a radius of uncertainty
     * around it, typically 5-20 m outdoors, much worse between tall buildings. As the driver approaches,
     * the honest thing to show is "nearly there" - and, more importantly, the arrival radius has to be
     * widened by the fix's own uncertainty <b>before</b> {@link #ARRIVED} can be claimed. See
     * {@link ArrivalEvaluator}.
     */
    APPROACHING,

    /**
     * The driver is inside the arrival radius.
     *
     * <p><b>This is a permission to ask, not a completion.</b> Reaching this state means the app may
     * offer the "Store completed?" dialog in Phase 5. It never means the store has been counted - no
     * count, marker colour or history row changes on the strength of this enum alone.
     */
    ARRIVED
}
