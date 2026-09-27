package com.cph.alpharoute.ui.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.cph.alpharoute.R;
import com.cph.alpharoute.domain.StoreStatus;

import org.junit.Test;

/**
 * Unit tests for the marker appearance rules in {@link MapMarkerController}.
 *
 * <p>These run on the JVM, no device and no emulator. The controller itself needs osmdroid and a
 * {@code MapView}, but {@code markerIconRes} is a plain lookup, so the rule that matters most can be
 * proved without any of that.
 *
 * <p><b>What is actually being protected here.</b> Phase 0.3 requires that marker colour is never the
 * only signal, because orange and green are the classic red/green-confusion pair. A user who cannot
 * separate those two colours must still be able to tell a pending store from a completed one - and the
 * only thing that makes that possible is the shapes being different: a filled circle against a check
 * mark.
 *
 * <p>That is a single-line change away from being broken. If someone ever "tidies up" the method to
 * return one icon for both statuses, every pending and completed store on the map becomes visually
 * identical for a colour-blind user, and nothing else in the codebase would notice. This test notices.
 *
 * <p><b>The second rule here is which coordinates may be drawn at all</b> (Phase 3.7). A store's
 * coordinate comes from a long-press and Home's from a tap, so neither can really be nonsense - but the
 * check also stands between a bug elsewhere and a pin placed somewhere the user never chose. Latitude 0
 * is in the Atlantic, and a NaN has no position at all, yet both would render as an ordinary-looking pin
 * that is simply in the wrong place.
 */
public class MapMarkerControllerTest {

    @Test
    public void pendingAndCompletedUseDifferentIcons() {
        assertNotEquals(
                "A pending store and a completed store must never look the same - the shapes carry the "
                        + "meaning because orange and green cannot",
                MapMarkerController.markerIconRes(StoreStatus.PENDING),
                MapMarkerController.markerIconRes(StoreStatus.COMPLETED));
    }

    @Test
    public void pendingUsesTheOrangeCirclePin() {
        assertEquals(R.drawable.ic_marker_pending, MapMarkerController.markerIconRes(StoreStatus.PENDING));
    }

    @Test
    public void completedUsesTheGreenCheckPin() {
        assertEquals(R.drawable.ic_marker_completed, MapMarkerController.markerIconRes(StoreStatus.COMPLETED));
    }

    @Test
    public void everyStatusResolvesToARealIcon() {
        // Guards against a status being added and forgotten, which would leave it drawing nothing.
        for (StoreStatus status : StoreStatus.values()) {
            assertTrue("No icon for " + status, MapMarkerController.markerIconRes(status) != 0);
        }
    }

    @Test
    public void theMappingIsDeterministic() {
        for (StoreStatus status : StoreStatus.values()) {
            assertEquals(MapMarkerController.markerIconRes(status), MapMarkerController.markerIconRes(status));
        }
    }

    // ------------------------------------------------------------------ can this coordinate be drawn?

    @Test
    public void aRealCoordinateIsPlottable() {
        assertTrue(MapMarkerController.hasPlottableCoordinates(14.5995, 120.9842));
    }

    @Test
    public void theEquatorAndThePrimeMeridianArePlottable() {
        // Zero is a REAL coordinate - it is in the Atlantic, off the coast of Africa - and the project's
        // rules say never to conflate it with "no value". Only null means absent.
        assertTrue(MapMarkerController.hasPlottableCoordinates(0.0, 0.0));
    }

    @Test
    public void aMissingLatitudeIsNotPlottable() {
        assertFalse(MapMarkerController.hasPlottableCoordinates(null, 120.9842));
    }

    @Test
    public void aMissingLongitudeIsNotPlottable() {
        assertFalse(MapMarkerController.hasPlottableCoordinates(14.5995, null));
    }

    @Test
    public void twoMissingCoordinatesAreNotPlottable() {
        // The "Home not set" case, which must remove the pin rather than draw one at (0, 0).
        assertFalse(MapMarkerController.hasPlottableCoordinates(null, null));
    }

    @Test
    public void aNaNLatitudeIsNotPlottable() {
        // A NaN has no position at all, and a pin built from one would look like a real store pinned in
        // the wrong place - the kind of wrong that is easy to believe.
        assertFalse(MapMarkerController.hasPlottableCoordinates(Double.NaN, 120.9842));
    }

    @Test
    public void aNaNLongitudeIsNotPlottable() {
        assertFalse(MapMarkerController.hasPlottableCoordinates(14.5995, Double.NaN));
    }

    @Test
    public void aLatitudeBeyondThePolesIsNotPlottable() {
        assertFalse(MapMarkerController.hasPlottableCoordinates(90.1, 120.9842));
        assertFalse(MapMarkerController.hasPlottableCoordinates(-90.1, 120.9842));
    }

    @Test
    public void aLongitudeBeyondTheDateLineIsNotPlottable() {
        assertFalse(MapMarkerController.hasPlottableCoordinates(14.5995, 180.1));
        assertFalse(MapMarkerController.hasPlottableCoordinates(14.5995, -180.1));
    }

    @Test
    public void theEdgeOfTheGlobeIsStillPlottable() {
        // The bounds are inclusive: the poles and the date line are real places.
        assertTrue(MapMarkerController.hasPlottableCoordinates(90.0, 180.0));
        assertTrue(MapMarkerController.hasPlottableCoordinates(-90.0, -180.0));
    }
}
