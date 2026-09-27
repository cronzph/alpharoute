package com.cph.alpharoute.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.StoreCandidate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The optimization request body.
 *
 * <p>Pure JVM. This is the highest-value test in Version 8, because <b>a wrong request produces a
 * confident, well-formed answer to the wrong question</b> - a route that visits every shop in a strange
 * order with nothing to indicate why. There is no error to catch; the only defence is asserting the body
 * field by field.
 */
public class OptimizationRequestBuilderTest {

    /** Manila. Real coordinates, so a swapped pair is visible rather than plausible. */
    private static final StoreCandidate STORE_A =
            new StoreCandidate(11, "A-01", "Alpha", 14.6100, 120.9800);
    private static final StoreCandidate STORE_B =
            new StoreCandidate(22, "B-01", "Bravo", 14.6200, 121.0100);

    private static final GeoCoordinate START = new GeoCoordinate(14.6000, 120.9800);
    private static final GeoCoordinate HOME = new GeoCoordinate(14.6500, 120.9800);

    private static List<StoreCandidate> list(StoreCandidate... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ------------------------------------------------------------------ the jobs

    @Test
    public void everyStoreBecomesAJob() throws JSONException {
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A, STORE_B), START, HOME);

        assertEquals(2, body.getJSONArray("jobs").length());
    }

    @Test
    public void theJobIdIsTheStoreId() throws JSONException {
        // This is what lets the response turn back into stores with no lookup table - and a lookup table is
        // a thing that can be wrong.
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A, STORE_B), START, HOME);

        JSONArray jobs = body.getJSONArray("jobs");
        assertEquals(11L, jobs.getJSONObject(0).getLong("id"));
        assertEquals(22L, jobs.getJSONObject(1).getLong("id"));
    }

    @Test
    public void theJobLocationIsLongitudeFirst() throws JSONException {
        // The single easiest mistake in the whole feature, and it is silent: a swapped pair is a perfectly
        // valid request for a point in the wrong place.
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A), START, HOME);

        JSONArray location = body.getJSONArray("jobs").getJSONObject(0).getJSONArray("location");
        assertEquals(120.9800, location.getDouble(0), 0.0001);
        assertEquals(14.6100, location.getDouble(1), 0.0001);
    }

    @Test
    public void theServiceTimeIsZero() throws JSONException {
        // The app has no idea how long a delivery takes, and inventing a number would change the order the
        // solver returns. Zero means "optimise the driving", which is the part the app knows.
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A), START, HOME);

        assertEquals(0, body.getJSONArray("jobs").getJSONObject(0).getInt("service"));
    }

    // ------------------------------------------------------------------ the vehicle

    @Test
    public void thereIsExactlyOneVehicle() throws JSONException {
        // A solo app with one driver and one car. Asking for more would produce a plan the user cannot
        // carry out.
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A, STORE_B), START, HOME);

        assertEquals(1, body.getJSONArray("vehicles").length());
    }

    @Test
    public void theVehicleCarriesTheDrivingProfile() throws JSONException {
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A), START, HOME);

        assertEquals(
                RoutingConfig.PROFILE_DRIVING_CAR,
                body.getJSONArray("vehicles").getJSONObject(0).getString("profile"));
    }

    @Test
    public void theStartAndEndAreLongitudeFirst() throws JSONException {
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A), START, HOME);
        JSONObject vehicle = body.getJSONArray("vehicles").getJSONObject(0);

        JSONArray start = vehicle.getJSONArray("start");
        assertEquals(120.9800, start.getDouble(0), 0.0001);
        assertEquals(14.6000, start.getDouble(1), 0.0001);

        JSONArray end = vehicle.getJSONArray("end");
        assertEquals(120.9800, end.getDouble(0), 0.0001);
        assertEquals(14.6500, end.getDouble(1), 0.0001);
    }

    // ------------------------------------------------------------------ the optional points

    @Test
    public void withoutAStartTheVehicleHasNoStart() throws JSONException {
        // No usable fix. The service then chooses where the route begins, which is better than inventing a
        // starting point the user is not at.
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A), null, HOME);

        assertFalse(body.getJSONArray("vehicles").getJSONObject(0).has("start"));
    }

    @Test
    public void withoutHomeTheVehicleHasNoEnd() throws JSONException {
        // Home is not set, so the route simply finishes at the last store.
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A), START, null);

        assertFalse(body.getJSONArray("vehicles").getJSONObject(0).has("end"));
    }

    @Test
    public void neitherPointIsPresentWhenNeitherIsKnown() throws JSONException {
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A), null, null);
        JSONObject vehicle = body.getJSONArray("vehicles").getJSONObject(0);

        assertFalse(vehicle.has("start"));
        assertFalse(vehicle.has("end"));
    }

    // ------------------------------------------------------------------ the one option sent

    @Test
    public void geometryIsRequestedBecauseItIsTheOnlyWayToGetADistance() throws JSONException {
        // This test used to assert the opposite, on the reasoning that geometry "roughly triples" the
        // response for a line the app does not draw. Both halves were wrong.
        //
        // The line really is ignored - OptimizationResponseParser never reads "geometry". But the service
        // sends NO distance anywhere without this option: not in the summary, not on the steps, only
        // duration and cost. So the route screen's summary line always read "0 m" and every per-stop leg
        // distance was zero. Measured on the live service 2026-09-27, a 50-stop request: 8,058 bytes
        // without this option and 13,304 with it - about 5 KB, not triple.
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A), START, HOME);

        assertTrue("without options.g the service returns no distance at all", body.has("options"));
        assertTrue("the geometry option must be on", body.getJSONObject("options").getBoolean("g"));
    }

    @Test
    public void nothingElseIsAskedForInOptions() throws JSONException {
        // Only "g". VROOM has a dozen options and every one of them changes the problem or the answer;
        // adding one without measuring what it does is how a request quietly stops describing the user's
        // route.
        JSONObject options = OptimizationRequestBuilder.build(list(STORE_A), START, HOME)
                .getJSONObject("options");

        assertEquals(1, options.length());
    }

    // ------------------------------------------------------------------ the refusals

    @Test
    public void anEmptyListIsRefused() {
        // A caller mistake, and the service would refuse it too. Failing here means the reason is in the
        // app's own terms.
        try {
            OptimizationRequestBuilder.build(Collections.emptyList(), START, HOME);
            fail("Expected an IllegalArgumentException for an empty list");
        } catch (IllegalArgumentException expected) {
            // Correct.
        } catch (JSONException e) {
            fail("Should have thrown IllegalArgumentException, not JSONException");
        }
    }

    @Test
    public void moreThanFiftyStopsIsRefused() {
        // The service's hard cap. Checked in the builder rather than at the call site, so every caller is
        // protected by the same rule.
        List<StoreCandidate> tooMany = new ArrayList<>();
        for (int i = 0; i < RoutingConfig.MAX_OPTIMIZATION_JOBS + 1; i++) {
            tooMany.add(new StoreCandidate(i + 1, "S" + i, "Store " + i, 14.6, 120.98));
        }

        try {
            OptimizationRequestBuilder.build(tooMany, START, HOME);
            fail("Expected an IllegalArgumentException for over the limit");
        } catch (IllegalArgumentException expected) {
            // Correct.
        } catch (JSONException e) {
            fail("Should have thrown IllegalArgumentException, not JSONException");
        }
    }

    @Test
    public void exactlyFiftyStopsIsAllowed() throws JSONException {
        // The boundary is inclusive - the service accepts 50, so the app must not refuse it one early.
        List<StoreCandidate> exactly = new ArrayList<>();
        for (int i = 0; i < RoutingConfig.MAX_OPTIMIZATION_JOBS; i++) {
            exactly.add(new StoreCandidate(i + 1, "S" + i, "Store " + i, 14.6, 120.98));
        }

        JSONObject body = OptimizationRequestBuilder.build(exactly, START, HOME);

        assertEquals(RoutingConfig.MAX_OPTIMIZATION_JOBS, body.getJSONArray("jobs").length());
    }

    @Test
    public void nullStoresAreSkippedRatherThanThrowing() throws JSONException {
        List<StoreCandidate> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(STORE_A);

        JSONObject body = OptimizationRequestBuilder.build(withNull, START, HOME);

        assertEquals(1, body.getJSONArray("jobs").length());
    }

    @Test
    public void theBodyIsValidJsonRoundTrippable() throws JSONException {
        // The body is serialised to a string and sent. Parsing it back proves the serialisation is sound.
        JSONObject body = OptimizationRequestBuilder.build(list(STORE_A, STORE_B), START, HOME);
        JSONObject reparsed = new JSONObject(body.toString());

        assertTrue(reparsed.has("jobs"));
        assertTrue(reparsed.has("vehicles"));
        assertEquals(2, reparsed.getJSONArray("jobs").length());
    }
}
