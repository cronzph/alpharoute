package com.cph.alpharoute.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.cph.alpharoute.domain.OptimizationPlan;
import com.cph.alpharoute.domain.OptimizationStop;

import org.junit.Test;

/**
 * Reading the optimization service's answer.
 *
 * <p>Pure JVM. The fixture is the shape VROOM documents: a {@code routes} array whose single route carries
 * a {@code steps} array, and a {@code summary} beside it.
 *
 * <p><b>The trap this file exists for:</b> a route's steps include a {@code "start"} and an {@code "end"},
 * both of which carry a location and a distance. Counting them as stops would put a phantom shop at each
 * end of every route and make the count wrong by two.
 */
public class OptimizationResponseParserTest {

    /**
     * A two-stop route with a start and an end.
     *
     * <p>The start and end steps carry an id of their own in some responses, which is exactly why the
     * parser filters on {@code "type"} rather than on the presence of an id.
     */
    private static final String TWO_STOPS = "{"
            + "\"code\":0,"
            + "\"routes\":[{"
            + "  \"vehicle\":1,"
            + "  \"steps\":["
            + "    {\"type\":\"start\",\"id\":0,\"location\":[120.98,14.60],"
            + "     \"arrival\":0,\"duration\":0,\"distance\":0},"
            + "    {\"type\":\"job\",\"id\":11,\"location\":[120.98,14.61],"
            + "     \"arrival\":120,\"duration\":120,\"distance\":1112},"
            + "    {\"type\":\"job\",\"id\":22,\"location\":[121.01,14.62],"
            + "     \"arrival\":400,\"duration\":280,\"distance\":2152},"
            + "    {\"type\":\"end\",\"id\":0,\"location\":[120.98,14.65],"
            + "     \"arrival\":700,\"duration\":300,\"distance\":3200}"
            + "  ],"
            + "  \"summary\":{\"distance\":6464,\"duration\":700},"
            + "  \"unassigned\":[]"
            + "}]}";

    /**
     * The shape the live service actually returns, captured verbatim on 2026-09-27.
     *
     * <p><b>Two differences from the fixture above, and both were bugs.</b> The totals live in a
     * {@code summary} at the <i>top level</i> of the response, not inside the route - and a job step
     * carries {@code "id"} and {@code "job"} with the same value, plus a {@code distance} that only appears
     * when geometry was requested.
     */
    private static final String REAL_SHAPE = "{"
            + "\"code\":0,"
            + "\"summary\":{\"cost\":1491,\"routes\":1,\"unassigned\":0,\"duration\":1491,\"distance\":14137},"
            + "\"unassigned\":[],"
            + "\"routes\":[{"
            + "  \"vehicle\":1,\"cost\":1491,\"duration\":1491,\"distance\":14137,"
            + "  \"steps\":["
            + "    {\"type\":\"start\",\"location\":[120.98,14.6],\"arrival\":0,\"duration\":0},"
            + "    {\"type\":\"job\",\"location\":[120.98,14.61],\"id\":1,\"job\":1,"
            + "     \"arrival\":229,\"duration\":229,\"distance\":1556},"
            + "    {\"type\":\"job\",\"location\":[121.01,14.62],\"id\":2,\"job\":2,"
            + "     \"arrival\":810,\"duration\":810,\"distance\":4863},"
            + "    {\"type\":\"end\",\"location\":[120.98,14.65],\"arrival\":1491,\"duration\":1491}"
            + "  ]"
            + "}]}";

    // ------------------------------------------------------------------ the happy path

    @Test
    public void aTwoStopRouteGivesTwoStops() {
        // Not four. The start and end steps are not shops.
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, true, true);

        assertNotNull(plan);
        assertEquals(2, plan.stopCount());
    }

    @Test
    public void theStopsCarryTheirStoreIds() {
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, true, true);

        assertEquals(11L, plan.stops().get(0).storeId());
        assertEquals(22L, plan.stops().get(1).storeId());
    }

    @Test
    public void theSequenceIsOneBased() {
        // "#1" for the first stop, so the screen can number them without adding one.
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, true, true);

        assertEquals(1, plan.stops().get(0).sequence());
        assertEquals(2, plan.stops().get(1).sequence());
    }

    @Test
    public void theOrderIsTheSolversOrderNotTheRequestsOrder() {
        // The whole point. The request listed them in whatever order they were sent; the answer is the
        // order to drive them in.
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, true, true);

        assertEquals(11L, plan.stops().get(0).storeId());
        assertEquals(22L, plan.stops().get(1).storeId());
    }

    @Test
    public void theSummaryIsRead() {
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, true, true);

        assertEquals(6464.0, plan.totalDistanceMetres(), 0.5);
        assertEquals(700L, plan.totalDurationSeconds());
    }

    @Test
    public void theLegDetailsAreRead() {
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, true, true);
        OptimizationStop first = plan.stops().get(0);

        assertEquals(1112.0, first.legDistanceMetres(), 0.5);
        assertEquals(120L, first.legDurationSeconds());
        assertEquals(120L, first.arrivalSeconds());
    }

    @Test
    public void theStartAndEndFlagsAreCarriedThrough() {
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, true, true);

        assertTrue(plan.startsAtCurrentLocation());
        assertTrue(plan.endsAtHome());
    }

    @Test
    public void theFlagsReflectWhatWasAskedFor() {
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, false, false);

        assertFalse(plan.startsAtCurrentLocation());
        assertFalse(plan.endsAtHome());
    }

    // ------------------------------------------------------------------ the real shape

    @Test
    public void theTotalsComeFromTheTopLevelSummary() {
        // THE bug. The parser looked for "summary" inside the route; VROOM puts it at the top level. Both
        // totals were therefore always zero, and the route screen - which promises "%d stops · distance ·
        // time" - always read "0 m".
        OptimizationPlan plan = OptimizationResponseParser.parse(REAL_SHAPE, true, true);

        assertNotNull(plan);
        assertEquals(14137.0, plan.totalDistanceMetres(), 0.5);
        assertEquals(1491L, plan.totalDurationSeconds());
    }

    @Test
    public void theRealShapeStillCountsOnlyTheJobs() {
        // The start and end steps in the live response have no id of their own, but they are still not
        // shops - so the filter has to stay on "type".
        OptimizationPlan plan = OptimizationResponseParser.parse(REAL_SHAPE, true, true);

        assertEquals(2, plan.stopCount());
    }

    @Test
    public void theRealShapeLegDistancesAreRead() {
        OptimizationPlan plan = OptimizationResponseParser.parse(REAL_SHAPE, true, true);

        assertEquals(1556.0, plan.stops().get(0).legDistanceMetres(), 0.5);
        assertEquals(4863.0, plan.stops().get(1).legDistanceMetres(), 0.5);
    }

    @Test
    public void aRouteLevelDistanceIsUsedWhenTheSummaryHasNone() {
        // The service omits "distance" from the summary unless geometry was requested, so a summary that
        // exists may still carry only a duration. Falling straight to zero there would report a real route
        // as 0 m long, which is the same class of bug as reading the wrong object.
        String body = "{"
                + "\"code\":0,"
                + "\"summary\":{\"duration\":1491},"
                + "\"routes\":[{\"duration\":1491,\"distance\":14137,"
                + "  \"steps\":[{\"type\":\"job\",\"id\":11,\"arrival\":10,\"distance\":100,\"duration\":10}]}]}";

        OptimizationPlan plan = OptimizationResponseParser.parse(body, true, false);

        assertNotNull(plan);
        assertEquals(14137.0, plan.totalDistanceMetres(), 0.5);
        assertEquals(1491L, plan.totalDurationSeconds());
    }

    // ------------------------------------------------------------------ unassigned

    @Test
    public void unassignedStoresAreCounted() {
        // A route that quietly leaves shops out is worse than one that says it could not fit them - the user
        // would drive home believing the day was done.
        String body = "{"
                + "\"code\":0,"
                + "\"routes\":[{"
                + "  \"steps\":[{\"type\":\"job\",\"id\":11,\"arrival\":10,\"distance\":100,\"duration\":10}],"
                + "  \"summary\":{\"distance\":100,\"duration\":10},"
                + "  \"unassigned\":[{\"id\":22}]"
                + "}]}";

        OptimizationPlan plan = OptimizationResponseParser.parse(body, true, false);

        assertNotNull(plan);
        assertEquals(1, plan.unassignedCount());
    }

    @Test
    public void unassignedAtTheTopLevelAreCountedToo() {
        // A request that fails validation puts them at the top rather than inside a route.
        String body = "{"
                + "\"code\":0,"
                + "\"routes\":[{\"steps\":[],\"summary\":{\"distance\":0,\"duration\":0}}],"
                + "\"unassigned\":[{\"id\":1},{\"id\":2}]}";

        OptimizationPlan plan = OptimizationResponseParser.parse(body, true, false);

        assertNotNull(plan);
        assertEquals(2, plan.unassignedCount());
    }

    @Test
    public void aCleanRouteHasNoUnassigned() {
        OptimizationPlan plan = OptimizationResponseParser.parse(TWO_STOPS, true, true);

        assertEquals(0, plan.unassignedCount());
    }

    // ------------------------------------------------------------------ the refusals

    @Test
    public void anEmptyBodyGivesNoPlan() {
        assertNull(OptimizationResponseParser.parse(null, true, true));
        assertNull(OptimizationResponseParser.parse("", true, true));
        assertNull(OptimizationResponseParser.parse("   ", true, true));
    }

    @Test
    public void aNonZeroCodeGivesNoPlan() {
        // VROOM reports its own failures inside a 200 response. Checking the status line alone would miss it
        // and the app would show an empty route as though it were a plan.
        String body = "{\"code\":3,\"error\":\"Input error\"}";

        assertNull(OptimizationResponseParser.parse(body, true, true));
    }

    @Test
    public void noRoutesGivesNoPlan() {
        assertNull(OptimizationResponseParser.parse("{\"code\":0,\"routes\":[]}", true, true));
    }

    @Test
    public void noStepsGivesNoPlan() {
        assertNull(OptimizationResponseParser.parse("{\"code\":0,\"routes\":[{}]}", true, true));
    }

    @Test
    public void malformedJsonGivesNoPlanRatherThanThrowing() {
        // A service problem, not a bug that should crash the screen.
        assertNull(OptimizationResponseParser.parse("not json at all", true, true));
    }

    // ------------------------------------------------------------------ defensive

    @Test
    public void aRouteOfOnlyStartAndEndGivesNoStops() {
        // Everything assigned to nowhere - a valid but empty answer. The plan exists and has no stops, so the
        // screen can say "nothing to show" rather than crashing on an empty list.
        String body = "{"
                + "\"code\":0,"
                + "\"routes\":[{"
                + "  \"steps\":["
                + "    {\"type\":\"start\",\"location\":[120.98,14.60],\"arrival\":0,\"distance\":0,\"duration\":0},"
                + "    {\"type\":\"end\",\"location\":[120.98,14.65],\"arrival\":10,\"distance\":10,\"duration\":10}"
                + "  ],"
                + "  \"summary\":{\"distance\":10,\"duration\":10}"
                + "}]}";

        OptimizationPlan plan = OptimizationResponseParser.parse(body, true, true);

        assertNotNull(plan);
        assertEquals(0, plan.stopCount());
        assertFalse(plan.hasStops());
    }

    @Test
    public void aMissingSummaryIsReadAsZeroRatherThanFailing() {
        String body = "{"
                + "\"code\":0,"
                + "\"routes\":[{"
                + "  \"steps\":[{\"type\":\"job\",\"id\":11,\"arrival\":10,\"distance\":100,\"duration\":10}]"
                + "}]}";

        OptimizationPlan plan = OptimizationResponseParser.parse(body, true, false);

        assertNotNull(plan);
        assertEquals(1, plan.stopCount());
        assertEquals(0.0, plan.totalDistanceMetres(), 0.0001);
    }

    @Test
    public void aJobWithNoIdIsStillRead() {
        // optLong with a fallback of -1 rather than a throw. A missing id is a service oddity, and the stop
        // still appears so the count stays right.
        String body = "{"
                + "\"code\":0,"
                + "\"routes\":[{"
                + "  \"steps\":[{\"type\":\"job\",\"arrival\":10,\"distance\":100,\"duration\":10}],"
                + "  \"summary\":{\"distance\":100,\"duration\":10}"
                + "}]}";

        OptimizationPlan plan = OptimizationResponseParser.parse(body, true, false);

        assertNotNull(plan);
        assertEquals(1, plan.stopCount());
        assertEquals(-1L, plan.stops().get(0).storeId());
    }

    @Test
    public void otherStepTypesAreIgnored() {
        // VROOM can emit a "break" step. It is not a store either.
        String body = "{"
                + "\"code\":0,"
                + "\"routes\":[{"
                + "  \"steps\":["
                + "    {\"type\":\"job\",\"id\":11,\"arrival\":10,\"distance\":100,\"duration\":10},"
                + "    {\"type\":\"break\",\"id\":0,\"arrival\":20,\"distance\":0,\"duration\":0},"
                + "    {\"type\":\"job\",\"id\":22,\"arrival\":30,\"distance\":50,\"duration\":10}"
                + "  ],"
                + "  \"summary\":{\"distance\":150,\"duration\":20}"
                + "}]}";

        OptimizationPlan plan = OptimizationResponseParser.parse(body, true, false);

        assertNotNull(plan);
        assertEquals(2, plan.stopCount());
    }
}
