package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

import java.util.Map;

/**
 * Suggests the store that is nearest to <b>drive</b> to, using real road distances.
 *
 * <p><b>Phase 8.2, and why it exists.</b> The straight-line strategies in 7.1 and 7.3 share one flaw: a
 * shop across a river is 400 m away as the crow flies and a 4 km drive. The nearest by straight line is
 * not always the nearest to drive to, and a suggestion that is confidently wrong is worse than one that
 * admits it is approximate.
 *
 * <p><b>It never becomes the only strategy.</b> The road distances come from a network service, and the
 * app works offline. When they are absent - not fetched yet, or the request failed - this delegates to
 * {@link HomeAwareStrategy}, which is the Phase 7 behaviour and needs no special case. So the app is never
 * worse off than it was before this phase; it is only better when the service answers.
 *
 * <h3>What it does, exactly</h3>
 *
 * <pre>
 *   for each pending store:
 *       if a road distance is known  -> use it
 *       else                          -> fall back to the straight line for that store
 *   pick the smallest
 * </pre>
 *
 * <p><b>Mixing the two measures is deliberate, and it is a real compromise.</b> A road distance is always
 * greater than or equal to the straight line between the same two points, so comparing them directly is
 * not strictly fair - a store with a known road distance can lose to one that only has a straight line.
 * The alternative is worse: refusing to answer at all when one leg of the matrix failed. And in practice
 * the matrix answers for every store in one request, so either all of them have road distances or none do.
 *
 * <p>Pure Java (AD-11).
 */
public final class RoadDistanceStrategy implements RouteSuggestionStrategy {

    /** Used when no road distances are available. Stateless, so one instance is enough. */
    private final RouteSuggestionStrategy fallback = new HomeAwareStrategy();

    @NonNull
    @Override
    public SuggestionResult suggest(@NonNull SuggestionContext context) {

        SuggestionResult terminal = SuggestionSupport.terminal(context);
        if (terminal != null) {
            return terminal;
        }

        if (!context.hasRoadDistances()) {
            // Offline, or the matrix has not answered yet. The Phase 7 behaviour, unchanged.
            return fallback.suggest(context);
        }

        Map<Long, Double> roadDistances = context.roadDistances();

        StoreCandidate best = null;
        double bestDistance = Double.NaN;
        boolean bestIsRoadDistance = false;

        for (StoreCandidate candidate : context.pending()) {
            if (candidate == null) {
                continue;
            }

            Double road = roadDistances.get(candidate.id());
            double distance;
            boolean isRoad;

            if (road != null && !road.isNaN()) {
                distance = road;
                isRoad = true;
            } else {
                // No road distance for this one store. The straight line is the best available estimate,
                // and using it is better than dropping the store from consideration entirely.
                distance = straightLineTo(context, candidate);
                isRoad = false;
            }

            if (Double.isNaN(distance)) {
                // Neither measurement could be made - an unusable coordinate and no road distance.
                continue;
            }

            if (best == null
                    || distance < bestDistance
                    || (distance == bestDistance && candidate.id() < best.id())) {
                best = candidate;
                bestDistance = distance;
                bestIsRoadDistance = isRoad;
            }
        }

        if (best == null) {
            // Every candidate was unmeasurable. The simpler strategies still have an answer.
            return fallback.suggest(context);
        }

        // The reason tells the truth about how the number was arrived at. A road distance and a straight
        // line are different claims, and the card must not present one as the other.
        SuggestionResult.Reason reason = bestIsRoadDistance
                ? SuggestionResult.Reason.ROAD_DISTANCE
                : SuggestionResult.Reason.NEAREST;

        return SuggestionResult.suggested(best, reason, bestDistance);
    }

    /**
     * The straight line from the user to a store, or {@code NaN} when there is no usable position.
     *
     * <p>Home is used when the user's position is unknown, matching {@link NearestStrategy} - so a
     * suggestion made without a fix still has a distance to show, and the reason says which reference was
     * used.
     */
    private static double straightLineTo(@NonNull SuggestionContext context,
                                         @NonNull StoreCandidate candidate) {
        if (context.hasLocation()) {
            return DistanceCalculator.distanceMetres(
                    context.currentLocation(), candidate.coordinate());
        }
        if (context.hasHome()) {
            return DistanceCalculator.distanceMetres(context.home(), candidate.coordinate());
        }
        return Double.NaN;
    }
}
