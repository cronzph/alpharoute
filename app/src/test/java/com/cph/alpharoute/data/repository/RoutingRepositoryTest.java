package com.cph.alpharoute.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cph.alpharoute.domain.GeoCoordinate;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for the routing request body built by {@link RoutingRepository}.
 *
 * <p>These run on the JVM with no network. The repository's request itself needs a connection and a key,
 * but the body it sends is a pure function of two points, so it can be checked here - which matters,
 * because <b>the coordinate order is the easiest mistake in this entire feature and it fails silently.</b>
 *
 * <p>A swapped pair produces a perfectly well-formed request for a point in the wrong place, and the
 * service answers with a perfectly well-formed route to that wrong place. Nothing errors. The map simply
 * draws a line to somewhere the user never asked to go. openrouteservice takes coordinates as
 * {@code [longitude, latitude]} - the opposite of how a coordinate is spoken - and
 * {@code RouteResponseParser} has to swap the answer back, so the two rules must stay symmetric.
 *
 * <p>The points used here are real places, used only as fixtures. No store coordinate is invented
 * anywhere in this project, and nothing in these tests touches the database.
 */
public class RoutingRepositoryTest {

    /** Manila, the app's operating region. */
    private static final GeoCoordinate ORIGIN = new GeoCoordinate(14.5995, 120.9842);
    private static final GeoCoordinate DESTINATION = new GeoCoordinate(14.6071, 120.9911);

    @Test
    public void theRequestBodyIsValidJson() throws Exception {
        JSONObject body = new JSONObject(RoutingRepository.buildRequestBody(ORIGIN, DESTINATION));

        assertTrue("The service expects a \"coordinates\" array", body.has("coordinates"));
    }

    @Test
    public void theRequestBodyCarriesExactlyTwoPoints() throws Exception {
        assertEquals(2, coordinatesOf(ORIGIN, DESTINATION).length());
    }

    @Test
    public void eachPointIsSentAsLongitudeThenLatitude() throws Exception {
        // 120.9842 is a longitude and 14.5995 is a latitude. Sending them the other way round asks the
        // service for a point in the Indian Ocean, and it would happily answer.
        JSONArray first = coordinatesOf(ORIGIN, DESTINATION).getJSONArray(0);

        assertEquals("first element is the longitude", 120.9842, first.getDouble(0), 1e-9);
        assertEquals("second element is the latitude", 14.5995, first.getDouble(1), 1e-9);
    }

    @Test
    public void theOriginIsSentBeforeTheDestination() throws Exception {
        JSONArray coordinates = coordinatesOf(ORIGIN, DESTINATION);

        assertEquals(120.9842, coordinates.getJSONArray(0).getDouble(0), 1e-9);
        assertEquals(120.9911, coordinates.getJSONArray(1).getDouble(0), 1e-9);
    }

    @Test
    public void aNegativeLongitudeIsStillSentFirst() throws Exception {
        // The case a "the larger number must be the longitude" shortcut would get wrong. Longitudes run
        // negative west of Greenwich, so that shortcut would swap the pair for anywhere in the Americas -
        // and for the Philippines it would happen to look correct, which is what makes it dangerous.
        GeoCoordinate west = new GeoCoordinate(38.9072, -77.0369);
        GeoCoordinate westDestination = new GeoCoordinate(38.9200, -77.0500);

        JSONArray first = coordinatesOf(west, westDestination).getJSONArray(0);

        assertEquals(-77.0369, first.getDouble(0), 1e-9);
        assertEquals(38.9072, first.getDouble(1), 1e-9);
    }

    private static JSONArray coordinatesOf(GeoCoordinate origin, GeoCoordinate destination) throws Exception {
        return new JSONObject(RoutingRepository.buildRequestBody(origin, destination))
                .getJSONArray("coordinates");
    }

    // ------------------------------------------------------------------ the matrix body (Phase 8.2)

    @Test
    public void theMatrixSendsIndicesForSourcesAndDestinations() throws Exception {
        // The service answers 400 "Parameter 'sources' has incorrect value or format." when these are
        // coordinate pairs rather than indices into "locations". Verified against the live service on
        // 2026-09-27. It is an easy mistake to make, because the directions endpoint takes bare pairs.
        JSONObject body = matrixBodyOf(ORIGIN, DESTINATION);

        assertEquals(0, body.getJSONArray("sources").getInt(0));
        assertEquals(1, body.getJSONArray("destinations").getInt(0));
    }

    @Test
    public void theMatrixDestinationsAreNumberedFromOne() throws Exception {
        // The origin occupies index 0 in "locations", so the destinations start at 1. Off by one here pairs
        // every distance with the wrong store while still producing a grid of exactly the width the parser
        // expects - so it would be accepted as a good answer rather than rejected.
        JSONObject body = matrixBodyOf(ORIGIN, DESTINATION, new GeoCoordinate(14.6200, 121.0100));

        JSONArray destinations = body.getJSONArray("destinations");
        assertEquals(2, destinations.length());
        assertEquals(1, destinations.getInt(0));
        assertEquals(2, destinations.getInt(1));
    }

    @Test
    public void theMatrixLocationsAreTheOriginThenEveryDestination() throws Exception {
        // The index arrays are only meaningful against this layout, so the two must be read together.
        JSONObject body = matrixBodyOf(ORIGIN, DESTINATION);

        JSONArray locations = body.getJSONArray("locations");
        assertEquals(2, locations.length());
        assertEquals("index 0 is the origin", 120.9842, locations.getJSONArray(0).getDouble(0), 1e-9);
        assertEquals("index 1 is the destination", 120.9911, locations.getJSONArray(1).getDouble(0), 1e-9);
    }

    @Test
    public void theMatrixAsksForDistancesOnly() throws Exception {
        // Durations would roughly double the response and the suggestion does not use them.
        JSONArray metrics = matrixBodyOf(ORIGIN, DESTINATION).getJSONArray("metrics");

        assertEquals(1, metrics.length());
        assertEquals("distance", metrics.getString(0));
    }

    private static JSONObject matrixBodyOf(GeoCoordinate origin, GeoCoordinate... destinations)
            throws Exception {
        return new JSONObject(RoutingRepository.buildMatrixBody(origin, Arrays.asList(destinations)));
    }
}
