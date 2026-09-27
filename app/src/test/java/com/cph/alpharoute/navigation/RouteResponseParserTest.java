package com.cph.alpharoute.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.cph.alpharoute.domain.RoutePlan;
import com.cph.alpharoute.domain.GeoCoordinate;

import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link RouteResponseParser} - what the routing service's answer turns into.
 *
 * <p>These run on the JVM. The parser is pure Java (AD-11) apart from {@code org.json}, which is why
 * {@code org.json:json} is on the test classpath - the platform's copy is a stub on the JVM where every
 * method throws "not mocked".
 *
 * <p><b>The fixture below is the real shape of a real answer.</b> It was captured from the live service on
 * 2026-09-25 for a drive across Manila: HTTP 200, a {@code FeatureCollection}, one {@code Feature} with a
 * {@code LineString} and a {@code summary}, and the attribution in {@code metadata}. The three coordinate
 * pairs are the real first three the service returned, and the summary numbers (1733.6 m, 298.4 s) are the
 * real ones. The line itself was trimmed from 31 points to 3 so the fixture stays readable - which is why
 * the summary and the line do not correspond to each other here. That is fine: the parser reads them
 * independently and never checks one against the other, so nothing is being tested that the trimming
 * could hide.
 *
 * <p><b>Two things matter more than the rest.</b>
 *
 * <ul>
 *   <li><b>The coordinate order.</b> GeoJSON is {@code [longitude, latitude]} - the opposite of how a
 *       coordinate is spoken. Swapping them puts the whole route in the sea off the coast of Africa, and
 *       nothing fails: the map draws a line, just in the wrong place.</li>
 *   <li><b>Refusing rather than guessing.</b> Every malformed case returns {@code null} so the screen can
 *       say the route is unavailable. A half-read route would draw a plausible-looking line that is
 *       wrong, and a driver following it is worse off than one who was told to try again.</li>
 * </ul>
 */
public class RouteResponseParserTest {

    /** A trimmed but structurally exact copy of a live response. See the class note. */
    private static final String LIVE_RESPONSE =
            "{"
            + "\"type\":\"FeatureCollection\","
            + "\"bbox\":[120.983448,14.599528,120.9911,14.6071],"
            + "\"features\":[{"
            +   "\"type\":\"Feature\","
            +   "\"bbox\":[120.983448,14.599528,120.9911,14.6071],"
            +   "\"geometry\":{"
            +     "\"type\":\"LineString\","
            +     "\"coordinates\":["
            +       "[120.984206,14.599528],"
            +       "[120.983729,14.599624],"
            +       "[120.983448,14.599649]"
            +     "]"
            +   "},"
            +   "\"properties\":{"
            +     "\"way_points\":[0,30],"
            +     "\"summary\":{\"distance\":1733.6,\"duration\":298.4}"
            +   "}"
            + "}],"
            + "\"metadata\":{\"attribution\":\"openrouteservice.org | OpenStreetMap contributors\"}"
            + "}";

    // ------------------------------------------------------------------ the happy path

    @Test
    public void aRealResponseBecomesARoute() {
        RoutePlan plan = RouteResponseParser.parse(LIVE_RESPONSE);

        assertNotNull("A real response must parse", plan);
    }

    @Test
    public void theDistanceAndDurationAreRoundedToWholeNumbers() {
        // The service returns 1733.6 and 298.4. Sub-metre precision means nothing for a drive, and an
        // int means a screen cannot end up showing "1733.6000000000001 m".
        RoutePlan plan = RouteResponseParser.parse(LIVE_RESPONSE);

        assertEquals(1734, plan.distanceMetres());
        assertEquals(298, plan.durationSeconds());
    }

    @Test
    public void everyPointOnTheLineIsKept() {
        RoutePlan plan = RouteResponseParser.parse(LIVE_RESPONSE);

        assertEquals(3, plan.points().size());
    }

    @Test
    public void thePointsStayInTheOrderTheServiceSentThem() {
        // The line is drawn by joining the points in order. Reversing them, or sorting them, would draw
        // a route nobody can drive.
        RoutePlan plan = RouteResponseParser.parse(LIVE_RESPONSE);
        List<GeoCoordinate> points = plan.points();

        assertEquals(14.599528, points.get(0).latitude(), 1e-9);
        assertEquals(14.599624, points.get(1).latitude(), 1e-9);
        assertEquals(14.599649, points.get(2).latitude(), 1e-9);
    }

    @Test
    public void geoJsonLongitudeFirstIsSwappedIntoLatitudeFirst() {
        // The headline test. The service sends [120.984206, 14.599528]; 120 is a longitude and 14 is a
        // latitude. If the parser did not swap them, every route would be drawn off the coast of Africa
        // - and nothing would fail to tell us.
        RoutePlan plan = RouteResponseParser.parse(LIVE_RESPONSE);
        GeoCoordinate first = plan.points().get(0);

        assertEquals("14.599528 is the latitude", 14.599528, first.latitude(), 1e-9);
        assertEquals("120.984206 is the longitude", 120.984206, first.longitude(), 1e-9);
    }

    // ------------------------------------------------------------------ bodies that are not a route

    @Test
    public void aMissingBodyIsNotARoute() {
        assertNull(RouteResponseParser.parse(null));
    }

    @Test
    public void anEmptyBodyIsNotARoute() {
        assertNull(RouteResponseParser.parse(""));
        assertNull(RouteResponseParser.parse("   "));
    }

    @Test
    public void textThatIsNotJsonIsNotARoute() {
        assertNull(RouteResponseParser.parse("<html>502 Bad Gateway</html>"));
    }

    @Test
    public void theServicesOwnErrorBodyIsNotARoute() {
        // What the service returns for a bad request - note that "error" is an OBJECT here, while the
        // 401 and 403 responses send it as a plain string. Neither shape has a "features" array, so
        // neither parses; this is here to pin that down.
        assertNull(RouteResponseParser.parse(
                "{\"error\":{\"code\":2003,\"message\":\"Parameter 'coordinates' has incorrect "
                        + "value or format.\"},\"info\":{\"engine\":{}}}"));
    }

    @Test
    public void aResponseWithNoFeaturesArrayIsNotARoute() {
        assertNull(RouteResponseParser.parse("{\"type\":\"FeatureCollection\"}"));
    }

    @Test
    public void aResponseWithAnEmptyFeatureListIsNotARoute() {
        assertNull(RouteResponseParser.parse("{\"type\":\"FeatureCollection\",\"features\":[]}"));
    }

    @Test
    public void aResponseWithNoGeometryIsNotARoute() {
        assertNull(RouteResponseParser.parse(
                "{\"features\":[{\"properties\":{\"summary\":{\"distance\":10,\"duration\":5}}}]}"));
    }

    @Test
    public void aLineOfOnePointIsNotARoute() {
        // One point cannot be drawn as a line. Accepting it would put a stray dot on the map and a
        // distance on the screen with nothing behind it.
        assertNull(RouteResponseParser.parse(responseWithCoordinates("[[120.984206,14.599528]]")));
    }

    @Test
    public void aCoordinatePairMissingItsLongitudeIsNotARoute() {
        assertNull(RouteResponseParser.parse(
                responseWithCoordinates("[[120.984206,14.599528],[120.983729]]")));
    }

    @Test
    public void aLineWithAnOutOfGlobeCoordinateIsRejectedEntirely() {
        // Not skipped - rejected. Skipping the bad pair would leave a line joining two points that are
        // not actually joined, and the map would draw a straight cut across whatever lies between them.
        assertNull(RouteResponseParser.parse(
                responseWithCoordinates("[[120.984206,14.599528],[120.983729,91.0]]")));
    }

    @Test
    public void aLineWithANonNumericCoordinateIsRejected() {
        assertNull(RouteResponseParser.parse(
                responseWithCoordinates("[[120.984206,14.599528],[\"north\",14.599649]]")));
    }

    @Test
    public void aResponseWithNoSummaryIsNotARoute() {
        // No distance and no duration means nothing to show. A route with a line but no numbers would
        // leave half the screen blank.
        assertNull(RouteResponseParser.parse(
                "{ \"features\":[{ \"geometry\":{\"coordinates\":"
                        + "[[120.984206,14.599528],[120.983448,14.599649]]}, \"properties\":{} }] }"));
    }

    @Test
    public void aResponseWithANonNumericDistanceIsNotARoute() {
        assertNull(RouteResponseParser.parse(
                "{ \"features\":[{ \"geometry\":{\"coordinates\":"
                        + "[[120.984206,14.599528],[120.983448,14.599649]]},"
                        + " \"properties\":{\"summary\":{\"distance\":\"far\",\"duration\":298.4}} }] }"));
    }

    // ------------------------------------------------------------------ helper

    /** A response with the given coordinates array and a valid summary, for the malformed-line cases. */
    private static String responseWithCoordinates(String coordinatesJson) {
        return "{ \"features\":[{"
                + "\"geometry\":{\"type\":\"LineString\",\"coordinates\":" + coordinatesJson + "},"
                + "\"properties\":{\"summary\":{\"distance\":1733.6,\"duration\":298.4}}"
                + "}] }";
    }
}
