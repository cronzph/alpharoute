package com.cph.alpharoute.domain;

import androidx.annotation.NonNull;

/**
 * Suggests the pending store that best balances "close to me now" against "close to home".
 *
 * <p><b>Phase 7.3.</b> The problem it solves: the nearest store is often <i>behind</i> the user relative to
 * home, so doing the nearest one first can leave the last shop of the day on the far side of the city from
 * the way back.
 *
 * <h3>The scoring formula, in full</h3>
 *
 * <pre>
 *   score(store) = distance(currentLocation, store) + distance(store, home)
 * </pre>
 *
 * The store with the **lowest** score wins. Ties are broken by the lowest id, so the answer does not
 * depend on the order the list arrived in.
 *
 * <h3>What the score means, and what it does not</h3>
 *
 * The score is the length of the trip you would commit to by visiting that store <b>next and then going
 * straight home</b>. Choosing the smallest is the sensible greedy move.
 *
 * <p><b>It is a heuristic, not an optimisation.</b> It looks exactly one store ahead: it does not consider
 * the stores after this one, so the order it produces is <i>not</i> the shortest possible route. A route of
 * five stores can be arranged into 120 orders and this examines none of them. Calling it "optimal" would be
 * a lie, and the card's wording says "good next stop", not "best route".
 *
 * <p>Both distances are straight lines over the globe. A store across a river can score well and still be a
 * long drive - Version 8 replaces the measurement, not the formula.
 *
 * <h3>When it declines</h3>
 *
 * The formula needs <b>both</b> a position and a Home. If either is missing it cannot be computed, and
 * rather than inventing one it delegates to {@link NearestStrategy} - which has a defined answer for every
 * case. Delegating rather than duplicating means the two strategies cannot develop different opinions about
 * what to do when there is no fix.
 *
 * <p>Pure Java (AD-11).
 */
public final class HomeAwareStrategy implements RouteSuggestionStrategy {

    /** Used when this strategy cannot compute a score. Stateless, so one instance is enough. */
    private final RouteSuggestionStrategy fallback = new NearestStrategy();

    @NonNull
    @Override
    public SuggestionResult suggest(@NonNull SuggestionContext context) {

        SuggestionResult terminal = SuggestionSupport.terminal(context);
        if (terminal != null) {
            return terminal;
        }

        // The formula needs a reference point and a destination. Without both, the question it answers
        // cannot be asked - so the simpler strategy answers a different one instead of this one guessing.
        if (!context.hasLocation() || !context.hasHome()) {
            return fallback.suggest(context);
        }

        StoreCandidate best = null;
        double bestScore = Double.NaN;
        double bestFromCurrent = Double.NaN;

        for (StoreCandidate candidate : context.pending()) {
            if (candidate == null) {
                continue;
            }

            double fromCurrent = DistanceCalculator.distanceMetres(
                    context.currentLocation(), candidate.coordinate());
            double toHome = DistanceCalculator.distanceMetres(
                    candidate.coordinate(), context.home());

            // Either measurement being unusable makes the whole score unusable. Treating a NaN as zero
            // would put a store with a broken coordinate at the top of the list.
            if (Double.isNaN(fromCurrent) || Double.isNaN(toHome)) {
                continue;
            }

            double score = fromCurrent + toHome;

            if (best == null
                    || score < bestScore
                    || (score == bestScore && candidate.id() < best.id())) {
                best = candidate;
                bestScore = score;
                bestFromCurrent = fromCurrent;
            }
        }

        if (best == null) {
            // Every candidate had an unusable coordinate. The simpler strategy still has an answer.
            return fallback.suggest(context);
        }

        // The distance reported is from the USER to the store, not the score. The score is a decision
        // value that mixes in the way home; showing it as a distance would be showing a number that does
        // not describe any single journey.
        return SuggestionResult.suggested(best, SuggestionResult.Reason.HOME_AWARE, bestFromCurrent);
    }
}
