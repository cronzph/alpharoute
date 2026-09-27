package com.cph.alpharoute.data.db;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

/**
 * Saved route plans (Version 18).
 *
 * <p><b>A plan is saved in one transaction.</b> A plan with no stops, or stops with no plan, is not a
 * half-saved plan - it is a row that says nothing. {@link #savePlan} writes both halves together and throws
 * to roll back, so the two tables can never disagree about what exists.
 */
@Dao
public interface RoutePlanDao {

    // ------------------------------------------------------------------ reading

    /** Every saved plan, newest first. Observed, so a save or a delete redraws the list. */
    @Query("SELECT * FROM route_plans ORDER BY createdAt DESC")
    LiveData<List<RoutePlanEntity>> observePlans();

    @Query("SELECT * FROM route_plans WHERE id = :planId LIMIT 1")
    RoutePlanEntity findPlan(long planId);

    /**
     * The plans made for a round, newest first, read once.
     *
     * <p>A one-shot read rather than the observed {@link #observePlans()}, for the reason the route screen
     * documents elsewhere: a screen that has to <i>act</i> on a list - build an order from it, then write it
     * back - needs a snapshot it owns, not a value that can change under it halfway through.
     */
    @Query("SELECT * FROM route_plans WHERE roundNumber = :roundNumber ORDER BY createdAt DESC")
    List<RoutePlanEntity> findPlansInRoundNow(int roundNumber);

    /** The stops of a plan, in driving order. */
    @Query("SELECT * FROM route_plan_stops WHERE planId = :planId ORDER BY position ASC")
    List<RoutePlanStopEntity> findStops(long planId);

    @Query("SELECT COUNT(*) FROM route_plans")
    int countPlans();

    /** How many plans were made for a round, for generating the next default name. */
    @Query("SELECT COUNT(*) FROM route_plans WHERE roundNumber = :roundNumber")
    int countPlansInRound(int roundNumber);

    // ------------------------------------------------------------------ writing

    @Insert
    long insertPlan(RoutePlanEntity plan);

    @Insert
    List<Long> insertStops(List<RoutePlanStopEntity> stops);

    @Query("DELETE FROM route_plans WHERE id = :planId")
    int deletePlan(long planId);

    /** Removes every stop of a plan. Used by {@link #replaceStops} - never on its own. */
    @Query("DELETE FROM route_plan_stops WHERE planId = :planId")
    int deleteStops(long planId);

    @Query("UPDATE route_plans SET name = :name WHERE id = :planId")
    int renamePlan(long planId, String name);

    /**
     * Refreshes a plan's headline figures after its order has changed.
     *
     * <p>Kept in step with the stops deliberately: a plan whose stored total describes the order it used to
     * have is worse than one with no total at all, because nothing on screen would reveal the mismatch.
     */
    @Query("UPDATE route_plans SET totalDistanceMetres = :distanceMetres, "
            + "totalDurationSeconds = :durationSeconds, "
            + "startedAtLocation = :startedAtLocation, "
            + "endedAtHome = :endedAtHome "
            + "WHERE id = :planId")
    int updatePlanSummary(long planId,
                          double distanceMetres,
                          long durationSeconds,
                          boolean startedAtLocation,
                          boolean endedAtHome);

    /**
     * Saves a plan and its stops together.
     *
     * <p><b>The plan is inserted first, because the stops reference its id</b> - and that id does not exist
     * until the insert returns. The caller passes stops with {@code planId} unset and this method fills it
     * in, so a caller cannot get the pairing wrong.
     *
     * @param plan  the plan, without an id
     * @param stops the stops in driving order, with {@code position} already set and {@code planId} unset
     * @return the new plan's id
     * @throws IllegalStateException if either insert failed. The throw rolls both back
     */
    @Transaction
    default long savePlan(@NonNull RoutePlanEntity plan,
                          @NonNull List<RoutePlanStopEntity> stops) {
        long planId = insertPlan(plan);
        if (planId <= 0L) {
            throw new IllegalStateException("Could not insert the route plan");
        }

        for (RoutePlanStopEntity stop : stops) {
            stop.planId = planId;
        }

        if (!stops.isEmpty()) {
            List<Long> stopIds = insertStops(stops);
            if (stopIds.size() != stops.size()) {
                throw new IllegalStateException("Could not insert every stop of the route plan");
            }
        }

        return planId;
    }

    /**
     * Replaces a plan's stops with a new order, in one transaction.
     *
     * <p><b>Delete-then-insert rather than an update per row.</b> The order is the answer, so a reorder
     * changes most rows at once - and the unique index on {@code (planId, position)} means updating them
     * one at a time would collide with itself halfway through (moving stop 3 to position 1 is fine, but
     * something already at position 1 has to get out of the way first). Clearing and rewriting avoids the
     * whole class of problem, and it is one statement either way.
     *
     * <p><b>The delete and the insert must not be separable.</b> Between them the plan has no stops, and a
     * crash or a rotation landing in that window would leave a plan that describes nothing. The transaction
     * is what makes that impossible.
     *
     * @param stops the new stops in driving order, with {@code position} already set. {@code planId} is
     *              filled in here so a caller cannot pair them wrongly
     * @throws IllegalStateException if the stops could not all be written. The throw rolls the delete back
     */
    @Transaction
    default void replaceStops(long planId, @NonNull List<RoutePlanStopEntity> stops) {
        deleteStops(planId);

        for (RoutePlanStopEntity stop : stops) {
            stop.planId = planId;
        }

        if (!stops.isEmpty() && insertStops(stops).size() != stops.size()) {
            throw new IllegalStateException("Could not insert every stop of route plan " + planId);
        }
    }

    /**
     * Deletes a plan.
     *
     * <p>The stops go with it through the foreign key's {@code CASCADE}, so there is no second delete here
     * and no way for them to be forgotten.
     *
     * @throws IllegalStateException when no plan was removed, so a delete that did nothing is reported
     *                               rather than looking like a success
     */
    @Transaction
    default void deletePlanOrThrow(long planId) {
        if (deletePlan(planId) != 1) {
            throw new IllegalStateException("No route plan with id " + planId);
        }
    }
}
