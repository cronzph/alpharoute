package com.cph.alpharoute.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.cph.alpharoute.domain.ArrivalState;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.location.LocationFix;

import org.junit.Rule;
import org.junit.Test;

/**
 * The navigation session's rules.
 *
 * <p><b>What is worth proving without a device.</b> The session is the join between the foreground service
 * and the screen, and the failures that matter are about <i>ordering</i> rather than about rendering:
 * a fix arriving after the session ended, a session ending twice, a snapshot read as one piece. Those are
 * exactly the failures an emulator hides and a JVM test catches.
 *
 * <p>{@link InstantTaskExecutorRule} is what makes this possible: it runs LiveData's work on the calling
 * thread, so {@code postValue} delivers before the assertion rather than on a main looper that does not
 * exist off a device.
 */
public class NavigationSessionTest {

    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final long STORE_ID = 7L;
    private static final GeoCoordinate DESTINATION = new GeoCoordinate(14.5995, 120.9842);
    private static final int RADIUS = 50;
    private static final long NOW = 1_700_000_000_000L;

    private static LocationFix fixMetresNorth(double metres, float accuracy) {
        double degrees = metres / 111_195.0;
        return new LocationFix(
                DESTINATION.latitude() + degrees, DESTINATION.longitude(), accuracy, NOW);
    }

    // ------------------------------------------------------------------ lifetime

    @Test
    public void aNewSessionIsInactive() {
        NavigationSession session = new NavigationSession();
        assertFalse(session.isActive());
        assertEquals(NavigationSession.NO_STORE, session.activeStoreId());
        assertNull(session.current());
    }

    @Test
    public void beginningASessionMakesItActive() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);

        assertTrue(session.isActive());
        assertEquals(STORE_ID, session.activeStoreId());
    }

    /**
     * A brand-new session has not measured anything, so it must not claim the driver is outside the
     * radius. {@code NO_FIX} is the honest answer and the one the sheet renders as "Waiting for your
     * location…".
     */
    @Test
    public void aNewSessionReportsNoFixRatherThanOutside() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);

        NavigationSession.Snapshot snapshot = session.current();
        assertEquals(ArrivalState.NO_FIX, snapshot.arrivalState());
        assertTrue(Double.isNaN(snapshot.distanceMetres()));
        assertFalse(snapshot.hasArrived());
    }

    @Test
    public void endingASessionEmitsNothingAndGoesInactive() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);
        session.end();

        assertFalse(session.isActive());
        assertNull(session.current());
    }

    @Test
    public void endingTwiceIsHarmless() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);
        session.end();
        session.end();

        assertFalse(session.isActive());
        assertNull(session.current());
    }

    // ------------------------------------------------------------------ updates

    @Test
    public void aFixInsideTheRadiusProducesArrival() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);
        session.update(fixMetresNorth(10, 5f), RADIUS, NOW);

        NavigationSession.Snapshot snapshot = session.current();
        assertEquals(ArrivalState.ARRIVED, snapshot.arrivalState());
        assertTrue(snapshot.hasArrived());
        assertFalse(Double.isNaN(snapshot.distanceMetres()));
    }

    @Test
    public void aFarAwayFixProducesANonZeroDistance() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);
        session.update(fixMetresNorth(1000, 5f), RADIUS, NOW);

        NavigationSession.Snapshot snapshot = session.current();
        assertEquals(ArrivalState.OUTSIDE, snapshot.arrivalState());
        assertEquals(1000.0, snapshot.distanceMetres(), 2.0);
    }

    /**
     * <b>The race that matters.</b> A location fix is delivered on the main thread, and "End navigation"
     * is a main-thread tap - so a fix can arrive in the gap after the session has ended. It must not
     * resurrect a session the user has closed: doing so would bring back a notification and a bar for a
     * drive that is over.
     */
    @Test
    public void aLateFixCannotResurrectAnEndedSession() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);
        session.end();

        session.update(fixMetresNorth(5, 5f), RADIUS, NOW);

        assertFalse("An ended session must stay ended", session.isActive());
        assertNull(session.current());
    }

    @Test
    public void aFixWithNoSessionIsIgnoredRatherThanThrowing() {
        NavigationSession session = new NavigationSession();
        session.update(fixMetresNorth(5, 5f), RADIUS, NOW);

        assertFalse(session.isActive());
        assertNull(session.current());
    }

    /**
     * The snapshot is one object, so a reader cannot catch a session mid-update. This asserts the property
     * directly: the id and the destination in a snapshot always describe the same session.
     */
    @Test
    public void aSnapshotAlwaysDescribesOneSession() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);
        session.update(fixMetresNorth(10, 5f), RADIUS, NOW);

        NavigationSession.Snapshot snapshot = session.current();
        assertEquals(STORE_ID, snapshot.storeId());
        assertEquals(DESTINATION, snapshot.destination());
    }

    @Test
    public void anUnusableFixProducesNoFixButKeepsTheSession() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);

        // Accuracy unknown - the arrival decision must refuse, and the session must stay alive so the
        // next real fix can still be acted on.
        session.update(new LocationFix(DESTINATION.latitude(), DESTINATION.longitude(), Float.NaN, NOW),
                RADIUS, NOW);

        NavigationSession.Snapshot snapshot = session.current();
        assertEquals(ArrivalState.NO_FIX, snapshot.arrivalState());
        assertTrue(session.isActive());
    }

    /**
     * The radius comes from the settings repository and can change between updates without the session
     * being rebuilt, so the same fix must produce different answers under different radii.
     */
    @Test
    public void theArrivalRadiusIsReadOnEveryUpdate() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);

        LocationFix fix = fixMetresNorth(300, 10f);
        session.update(fix, RADIUS, NOW);
        assertEquals(ArrivalState.OUTSIDE, session.current().arrivalState());

        session.update(fix, 500, NOW);
        assertEquals(ArrivalState.ARRIVED, session.current().arrivalState());
    }

    // ------------------------------------------------------------------ starting again

    @Test
    public void beginningASecondSessionReplacesTheFirst() {
        NavigationSession session = new NavigationSession();
        session.begin(STORE_ID, DESTINATION);

        GeoCoordinate other = new GeoCoordinate(10.3157, 123.8854);
        session.begin(9L, other);

        assertEquals(9L, session.activeStoreId());
        assertEquals(other, session.current().destination());
    }
}
