package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

/**
 * Suggests the pending store closest to the user.
 *
 * <p><b>Phase 7.1.</b> The simplest strategy, and the one the app uses unless the Home-aware one is
 * chosen in Settings.
 *
 * <p><b>It is approximate, and it says so.</b> The distance is a straight line over the globe, not a road
 * distance: a store 400 m away across a river may be a 4 km drive. The suggestion is a hint about which
 * shop to head towards, not a route - Version 8 is where road distances arrive, and the card's wording
 * keeps that distinction visible.
 *
 * <p><b>It degrades rather than giving up.</b> There are three possible reference points, and the strategy
 * uses the best one available:
 *
 * <ol>
 *   <li><b>the user's position</b> - the real answer, {@link SuggestionResult.Reason#NEAREST};</li>
 *   <li><b>Home</b>, when there is no usable fix - a real answer to "which one should I do first if I am
 *       setting out", labelled {@link SuggestionResult.Reason#FROM_HOME} so the card does not claim the
 *       user is somewhere they are not;</li>
 *   <li><b>the lowest id</b>, when neither exists - arbitrary, and labelled
 *       {@link SuggestionResult.Reason#NO_REFERENCE}.</li>
 * </ol>
 *
 * Returning nothing in cases 2 and 3 would be defensible, but a card that says "no suggestion" when the
 * app could name a store is less useful than one that names a store and is honest about why.
 *
 * <p>Pure Java (AD-11), so every branch above is tested on the JVM.
 */
public final class NearestStrategy implements RouteSuggestionStrategy {

    @NonNull
    @Override
    public SuggestionResult suggest(@NonNull SuggestionContext context) {

        // No stores at all, or nothing left to visit. Both end the suggestion before a distance is
        // measured, and they need different messages - see SuggestionSupport.terminal.
        SuggestionResult terminal = SuggestionSupport.terminal(context);
        if (terminal != null) {
            return terminal;
        }

        // The only store left needs no comparison, and saying "the only one left" is more useful than
        // saying "nearest" about a choice that was not made.
        if (SuggestionSupport.countReal(context.pending()) == 1) {
            StoreCandidate only = SuggestionSupport.firstById(context.pending());
            if (only != null) {
                double distance = distanceFrom(context.currentLocation(), only);
                return SuggestionResult.suggested(only, SuggestionResult.Reason.ONLY_ONE_LEFT, distance);
            }
        }

        // 1. From where the user is.
        if (context.hasLocation()) {
            SuggestionSupport.Scored nearest =
                    SuggestionSupport.nearest(context.currentLocation(), context.pending());
            if (nearest != null) {
                return SuggestionResult.suggested(
                        nearest.store, SuggestionResult.Reason.NEAREST, nearest.distanceMetres);
            }
        }

        // 2. From Home. The distance reported is the distance from HOME, because that is what was measured
        //    - reporting it as a distance from the user would be a claim the app cannot make.
        if (context.hasHome()) {
            SuggestionSupport.Scored nearest =
                    SuggestionSupport.nearest(context.home(), context.pending());
            if (nearest != null) {
                return SuggestionResult.suggested(
                        nearest.store, SuggestionResult.Reason.FROM_HOME, nearest.distanceMetres);
            }
        }

        // 3. No reference at all. Arbitrary, and labelled as such.
        StoreCandidate fallback = SuggestionSupport.firstById(context.pending());
        if (fallback == null) {
            // Every candidate was null. Nothing to suggest, and nothing to say about it.
            return SuggestionResult.routeComplete();
        }
        return SuggestionResult.suggested(
                fallback, SuggestionResult.Reason.NO_REFERENCE, Double.NaN);
    }

    /**
     * The distance from a reference to a store, or {@code NaN} when there is no reference.
     *
     * <p>{@code NaN} rather than {@code 0}: a suggestion made without a position has no distance, and a
     * zero would be a claim that the user is standing in the shop.
     */
    private static double distanceFrom(GeoCoordinate reference, @NonNull StoreCandidate store) {
        if (reference == null) {
            return Double.NaN;
        }
        return DistanceCalculator.distanceMetres(reference, store.coordinate());
    }
}
