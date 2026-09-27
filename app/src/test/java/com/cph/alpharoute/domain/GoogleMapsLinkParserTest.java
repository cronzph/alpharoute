package com.cph.alpharoute.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link GoogleMapsLinkParser} - reading a location out of a Google Maps link.
 *
 * <p>These run on the JVM, because the parser is pure Java (AD-11).
 *
 * <p><b>The first test is the reason this class exists.</b> A Google Maps place link carries two
 * coordinate pairs: the {@code @} pair is where the camera was looking, and the {@code !3d}/{@code !4d}
 * pair is the place itself. They can be 164 metres apart, and this app's arrival radius is 50 metres. Take
 * the wrong one and the store is pinned on the wrong side of the building, the app never once offers to
 * complete it, and nothing anywhere reports a problem.
 */
public class GoogleMapsLinkParserTest {

    /** A realistic place link: the camera pair and the place pair deliberately differ. */
    private static final String PLACE_LINK =
            "https://www.google.com/maps/place/Alfamart+San+Roque/@14.6010,120.9900,17z/"
            + "data=!3m1!4b1!4m6!3m5!1s0x3397c9!8m2!3d14.599528!4d120.984206!16s%2Fg%2F11abc";

    // ------------------------------------------------------------------ the trap

    @Test
    public void aPlaceLinkReadsThePlaceNotTheCamera() {
        GeoCoordinate found = GoogleMapsLinkParser.parse(PLACE_LINK);

        assertNotNull("A place link must be readable", found);
        assertEquals("The latitude must be the PLACE's, from !3d", 14.599528, found.latitude(), 1e-9);
        assertEquals("The longitude must be the PLACE's, from !4d", 120.984206, found.longitude(), 1e-9);
    }

    @Test
    public void theCameraPairIsNotUsedWhenAPlacePairExists() {
        // The regression guard. If the @ pattern is ever tried before !3d, this fails - and the difference
        // is roughly 164 m, which is more than three times the default 50 m arrival radius.
        GeoCoordinate found = GoogleMapsLinkParser.parse(PLACE_LINK);

        assertNotEquals("The camera latitude must not be used", 14.6010, found.latitude(), 1e-9);
        assertNotEquals("The camera longitude must not be used", 120.9900, found.longitude(), 1e-9);
    }

    @Test
    public void theCameraIsUsedWhenThereIsNoPlacePair() {
        // A plain "@" link has nothing better in it, so the camera centre is a reasonable answer - it is
        // a real coordinate, just a less precise one.
        GeoCoordinate found = GoogleMapsLinkParser.parse(
                "https://www.google.com/maps/@14.599528,120.984206,17z");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
        assertEquals(120.984206, found.longitude(), 1e-9);
    }

    // ------------------------------------------------------------------ the formats that carry coordinates

    @Test
    public void aQueryParameterIsRead() {
        GeoCoordinate found = GoogleMapsLinkParser.parse("https://maps.google.com/?q=14.599528,120.984206");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
        assertEquals(120.984206, found.longitude(), 1e-9);
    }

    @Test
    public void aPercentEncodedCommaIsRead() {
        // How the same link looks once something has escaped it - in a message, or a share intent.
        GeoCoordinate found = GoogleMapsLinkParser.parse(
                "https://maps.google.com/?q=14.599528%2C120.984206");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
        assertEquals(120.984206, found.longitude(), 1e-9);
    }

    @Test
    public void aSearchApiLinkIsRead() {
        GeoCoordinate found = GoogleMapsLinkParser.parse(
                "https://www.google.com/maps/search/?api=1&query=14.599528,120.984206");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
        assertEquals(120.984206, found.longitude(), 1e-9);
    }

    @Test
    public void aGeoUriIsRead() {
        GeoCoordinate found = GoogleMapsLinkParser.parse("geo:14.599528,120.984206");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
        assertEquals(120.984206, found.longitude(), 1e-9);
    }

    @Test
    public void aGeoUriWithARepeatedQueryIsRead() {
        // Android's own share produces geo: with the coordinates twice. The part before the '?' is used.
        GeoCoordinate found = GoogleMapsLinkParser.parse(
                "geo:14.599528,120.984206?q=14.599528,120.984206");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
        assertEquals(120.984206, found.longitude(), 1e-9);
    }

    @Test
    public void aBareCoordinatePairIsRead() {
        // Typed or copied straight from anywhere - it does not have to be a link at all.
        GeoCoordinate found = GoogleMapsLinkParser.parse("14.599528, 120.984206");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
        assertEquals(120.984206, found.longitude(), 1e-9);
    }

    @Test
    public void aBareCoordinatePairToleratesSurroundingSpace() {
        GeoCoordinate found = GoogleMapsLinkParser.parse("   14.599528 ,  120.984206  ");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
    }

    @Test
    public void aShareBlobWithThePlaceNameOnItsOwnLineIsRead() {
        // What the clipboard actually holds after Share. The name line is not a URL and must be ignored.
        GeoCoordinate found = GoogleMapsLinkParser.parse(
                "Alfamart San Roque\nhttps://www.google.com/maps/place/Alfamart/@14.6010,120.9900,17z/"
                + "data=!3m1!4b1!4m6!3m5!1s0x3397c9!8m2!3d14.599528!4d120.984206");

        assertNotNull(found);
        assertEquals(14.599528, found.latitude(), 1e-9);
        assertEquals(120.984206, found.longitude(), 1e-9);
    }

    @Test
    public void aBarePairWithALabelInFrontIsNotRead() {
        // Documenting a deliberate limit. The bare-coordinate pattern is anchored, because an unanchored
        // one would match the numbers buried inside a URL and quietly win the precedence race. The cost is
        // that a labelled pair like this is not read - and it is a cost worth paying, because Google Maps
        // never produces this shape and a wrong pin is worse than no pin.
        assertNull(GoogleMapsLinkParser.parse("Location: 14.5, 120.9"));
    }

    // ------------------------------------------------------------------ signs, poles and zero

    @Test
    public void negativeCoordinatesAreRead() {
        // West of Greenwich and south of the equator. A pattern that only allowed digits would fail here.
        GeoCoordinate found = GoogleMapsLinkParser.parse(
                "https://www.google.com/maps/@-33.868820,151.209290,17z");

        assertNotNull(found);
        assertEquals(-33.868820, found.latitude(), 1e-9);
        assertEquals(151.209290, found.longitude(), 1e-9);
    }

    @Test
    public void zeroIsARealCoordinateNotAMissingOne() {
        // The project's rule: 0.0 is a place on Earth, and only null means absent.
        GeoCoordinate found = GoogleMapsLinkParser.parse("@0.0,0.0");

        assertNotNull(found);
        assertEquals(0.0, found.latitude(), 1e-9);
        assertEquals(0.0, found.longitude(), 1e-9);
    }

    @Test
    public void theEdgeOfTheGlobeIsAccepted() {
        assertNotNull(GoogleMapsLinkParser.parse("@90.0,180.0"));
        assertNotNull(GoogleMapsLinkParser.parse("@-90.0,-180.0"));
    }

    @Test
    public void aLatitudeBeyondThePolesIsRejected() {
        assertNull(GoogleMapsLinkParser.parse("@90.1,120.984206"));
        assertNull(GoogleMapsLinkParser.parse("@-90.1,120.984206"));
    }

    @Test
    public void aLongitudeBeyondTheDateLineIsRejected() {
        assertNull(GoogleMapsLinkParser.parse("@14.599528,180.1"));
        assertNull(GoogleMapsLinkParser.parse("@14.599528,-180.1"));
    }

    // ------------------------------------------------------------------ text that is not a location

    @Test
    public void nothingAtAllIsNotALocation() {
        assertNull(GoogleMapsLinkParser.parse(null));
    }

    @Test
    public void emptyTextIsNotALocation() {
        assertNull(GoogleMapsLinkParser.parse(""));
        assertNull(GoogleMapsLinkParser.parse("   "));
    }

    @Test
    public void aPlaceNameAloneIsNotALocation() {
        // Sharing sometimes puts the name on its own line, and if only that reached us there is nothing to
        // read. Better to say so than to pin the store at 0, 0.
        assertNull(GoogleMapsLinkParser.parse("Alfamart San Roque"));
    }

    @Test
    public void aWebsiteWithNoCoordinatesIsNotALocation() {
        assertNull(GoogleMapsLinkParser.parse("https://www.alfamart.com.ph/store-locator/"));
    }

    @Test
    public void aShortLinkIsNotReadableHere() {
        // It carries no coordinates; the repository has to follow it first. Returning null is correct -
        // the caller checks isShortLink() and resolves it instead of treating it as a failure.
        assertNull(GoogleMapsLinkParser.parse("https://maps.app.goo.gl/AbCdEfGh"));
    }

    // ------------------------------------------------------------------ telling a short link apart

    @Test
    public void theCurrentShortHostIsRecognised() {
        assertTrue(GoogleMapsLinkParser.isShortLink("https://maps.app.goo.gl/AbCdEfGh"));
    }

    @Test
    public void theLegacyShortHostIsRecognised() {
        assertTrue(GoogleMapsLinkParser.isShortLink("https://goo.gl/maps/AbCdEfGh"));
    }

    @Test
    public void aShortLinkIsRecognisedInsideAShareBlob() {
        assertTrue(GoogleMapsLinkParser.isShortLink(
                "Alfamart San Roque\nhttps://maps.app.goo.gl/AbCdEfGh"));
    }

    @Test
    public void aFullLinkIsNotAShortLink() {
        assertFalse(GoogleMapsLinkParser.isShortLink(PLACE_LINK));
    }

    @Test
    public void aBareCoordinatePairIsNotAShortLink() {
        assertFalse(GoogleMapsLinkParser.isShortLink("14.599528, 120.984206"));
    }

    @Test
    public void nothingIsNotAShortLink() {
        assertFalse(GoogleMapsLinkParser.isShortLink(null));
        assertFalse(GoogleMapsLinkParser.isShortLink(""));
    }
}
