package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Shared arithmetic for the suggestion strategies.
 *
 * <p>Package-private, and deliberately not part of the public API: it is the piece of the "nearest"
 * question that both {@link NearestStrategy} and {@link HomeAwareStrategy} have to answer, and writing it
 * twice would give the two strategies subtly different ideas of what "nearest" means.
 *
 * <p>Pure Java.
 */
final class SuggestionSupport {

    /** Utility class - never instantiated. */
    private SuggestionSupport() {
    }

    /** A candidate together with how far away it is. */
    static final class Scored {
        final StoreCandidate store;
        final double distanceMetres;

        Scored(StoreCandidate store, double distanceMetres) {
            this.store = store;
            this.distanceMetres = distanceMetres;
        }
    }

    /**
     * The candidate closest to a reference point.
     *
     * <p><b>Ties are broken by id, lowest first.</b> Two stores can be the same distance away - they can
     * even be at the same coordinates - and without a rule the choice would depend on the order the list
     * happened to arrive in. That would make the suggestion change when nothing changed, which is the kind
     * of flicker that makes a user stop trusting it.
     *
     * <p><b>A candidate whose distance cannot be computed is skipped</b> rather than treated as zero.
     * {@code DistanceCalculator} returns {@code NaN} for an unusable coordinate, and a {@code NaN} that was
     * allowed to win the comparison would put a store on the other side of the planet at the top of the
     * list.
     *
     * @return the nearest candidate, or {@code null} when the list is empty or nothing could be measured
     */
    @Nullable
    static Scored nearest(@Nullable GeoCoordinate reference,
                          @Nullable List<StoreCandidate> candidates) {
        if (reference == null || candidates == null || candidates.isEmpty()) {
            return null;
        }

        Scored best = null;
        for (StoreCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }

            double distance = DistanceCalculator.distanceMetres(reference, candidate.coordinate());
            if (Double.isNaN(distance)) {
                // An unusable coordinate. Skipping is the only safe reading - see the class note.
                continue;
            }

            if (best == null
                    || distance < best.distanceMetres
                    || (distance == best.distanceMetres && candidate.id() < best.store.id())) {
                best = new Scored(candidate, distance);
            }
        }
        return best;
    }

    /**
     * The first candidate by id.
     *
     * <p>The last resort, used only when there is neither a position nor a Home to measure from. It is
     * arbitrary, which is why {@link SuggestionResult.Reason#NO_REFERENCE} exists - the card says so
     * rather than presenting it as a considered choice.
     */
    @Nullable
    static StoreCandidate firstById(@Nullable List<StoreCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        StoreCandidate best = null;
        for (StoreCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.id() < best.id()) {
                best = candidate;
            }
        }
        return best;
    }

    /** How many real (non-null) candidates a list holds. */
    static int countReal(@Nullable List<StoreCandidate> candidates) {
        if (candidates == null) {
            return 0;
        }
        int count = 0;
        for (StoreCandidate candidate : candidates) {
            if (candidate != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * The answer when there is nothing to choose between.
     *
     * <p>Two states end a suggestion before any distance is measured, and they need different messages:
     * <b>no stores at all</b> (an empty app, which needs the "add your first store" prompt) and
     * <b>nothing pending</b> (the round is finished, which is a success and offers Home instead).
     *
     * <p>The pending list alone cannot tell them apart - both are empty - which is why the context carries
     * the total as well.
     *
     * <p>Written once here rather than in each strategy, so the two cannot develop different ideas about
     * which state the app is in.
     *
     * @return the terminal result, or {@code null} when there are stores to choose from and the strategy
     *         should carry on
     */
    @Nullable
    static SuggestionResult terminal(@NonNull SuggestionContext context) {
        if (context.totalStores() == 0) {
            return SuggestionResult.noStores();
        }
        if (!context.hasPending()) {
            return SuggestionResult.routeComplete();
        }
        return null;
    }
}
