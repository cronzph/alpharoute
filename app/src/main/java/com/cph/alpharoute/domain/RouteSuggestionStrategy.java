package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

/**
 * How the app decides which store to visit next.
 *
 * <p><b>Architecture decision AD-12.</b> The choice is behind this interface so the rule can be swapped
 * without anything else changing: 7.1 uses the straight-line nearest store, 7.3 adds the Home-aware
 * heuristic, and Version 8 replaces both with road-based distances. The Dashboard card asks for a
 * {@link SuggestionResult} and does not know which strategy produced it.
 *
 * <p><b>An implementation must be pure.</b> No database, no network, no clock, no Android. Everything it
 * is allowed to look at arrives in the {@link SuggestionContext}, which is what makes each strategy
 * testable on the JVM - and a suggestion rule that cannot be tested is one nobody can trust, because a
 * wrong answer looks exactly like a right one.
 *
 * <p><b>An implementation must not throw on a sparse context.</b> No location, no Home and no pending
 * stores are all ordinary states, and each has a defined answer. Returning a {@link SuggestionResult} that
 * says which one it is beats throwing and beats guessing.
 */
public interface RouteSuggestionStrategy {

    /**
     * Chooses the next store to visit.
     *
     * @param context everything the strategy may look at. Never {@code null}
     * @return the decision. Never {@code null}
     */
    @NonNull
    SuggestionResult suggest(@NonNull SuggestionContext context);
}
