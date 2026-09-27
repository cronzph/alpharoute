package com.cph.alpharoute.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cph.alpharoute.domain.ArrivalState;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.location.LocationFix;

/**
 * The active navigation session: which store the driver is on their way to, and how close they are.
 *
 * <p><b>Why this object exists rather than the service and the screens talking directly.</b> While a
 * session is running, two things that cannot see each other have to agree:
 *
 * <ul>
 *   <li>the <b>foreground service</b>, which watches the position and may have no screen to talk to;</li>
 *   <li>the <b>Navigation Active sheet</b>, which shows the distance and the arrival indicator.</li>
 * </ul>
 *
 * <p>They must not hold references to one another. A service that held a Fragment would leak it; a
 * Fragment that bound to the service would have to rebind on every rotation and would show a stale
 * distance in between. Instead both read and write one observed object, which is the same LiveData
 * pattern the rest of the app uses (AD-2, AD-4) - the map's blue dot, the route line and the store
 * markers all already work this way.
 *
 * <p><b>It holds no database rows.</b> The session knows a store <i>id</i> and a coordinate, never a
 * {@code StoreEntity}. The screen that shows the store's code and name reads it from Room itself, so Room
 * stays the single source of truth (AD-4) and a renamed store cannot leave a stale name on screen.
 *
 * <p>Held by {@link com.cph.alpharoute.AlphaRouteApp} for the life of the process, so a session survives
 * a rotation, a tab switch and a screen being destroyed while Google Maps is in front.
 */
public class NavigationSession {

    /**
     * Everything one session is made of.
     *
     * <p>One immutable object per change rather than several mutable fields, for the same reason
     * {@code RouteState} is one object: a screen that read {@code isActive()} and then
     * {@code destination()} as two separate calls could catch the session being ended in between, and
     * render an active-looking sheet with no destination in it.
     */
    public static final class Snapshot {

        private final long storeId;
        private final GeoCoordinate destination;
        private final ArrivalState arrivalState;
        private final double distanceMetres;

        private Snapshot(long storeId,
                         @NonNull GeoCoordinate destination,
                         @NonNull ArrivalState arrivalState,
                         double distanceMetres) {
            this.storeId = storeId;
            this.destination = destination;
            this.arrivalState = arrivalState;
            this.distanceMetres = distanceMetres;
        }

        /** The store being navigated to. Never {@link #NO_STORE}. */
        public long storeId() {
            return storeId;
        }

        /** Where the drive ends. */
        @NonNull
        public GeoCoordinate destination() {
            return destination;
        }

        /** How close the driver is. */
        @NonNull
        public ArrivalState arrivalState() {
            return arrivalState;
        }

        /**
         * The straight-line distance to the destination in metres, or {@link Double#NaN} when it cannot
         * be known - see {@link com.cph.alpharoute.domain.DistanceCalculator}.
         *
         * <p>Straight-line, not driving distance, and the screen says so. The driving distance would mean
         * asking the routing service again on every fix, which would spend the free plan's daily
         * allowance in minutes and would be a worse answer anyway - it would describe a route the driver
         * may have already left.
         */
        public double distanceMetres() {
            return distanceMetres;
        }

        /** True once the driver is inside the arrival radius. The only state that may offer a completion. */
        public boolean hasArrived() {
            return arrivalState == ArrivalState.ARRIVED;
        }
    }

    /** No session is running. Room's {@code autoGenerate} keys start at 1, so -1 can never be a store. */
    public static final long NO_STORE = -1L;

    private final MutableLiveData<Snapshot> snapshot = new MutableLiveData<>(null);

    /** Which store is active, or {@code NO_STORE}. */
    private long activeStoreId = NO_STORE;

    /**
     * Whether the monitoring service is currently running.
     *
     * <p><b>Why the session tracks this rather than the sheet asking.</b> A service can stop without the
     * user asking it to - the system reclaims it under memory pressure, or location permission is revoked
     * and the service stops itself. When that happens the session is still active but nothing is watching,
     * and the sheet has to say so. Without this flag the sheet would show a live distance for a drive that
     * is no longer being monitored, which is the most misleading state this screen could be in.
     *
     * <p>Set by the service itself in {@code onCreate} and {@code onDestroy}, so it is always the service's
     * own account of whether it is running.
     */
    private boolean monitoring;

    /**
     * The session, observed. Emits {@code null} whenever no navigation is active, which is the state the
     * sheet reads as "hide me entirely".
     */
    public LiveData<Snapshot> observe() {
        return snapshot;
    }

    /** True while the foreground service is running. False means the drive is not being watched. */
    public boolean isMonitoring() {
        return monitoring;
    }

    /** Called by the service when it starts. */
    public void setMonitoring(boolean monitoring) {
        this.monitoring = monitoring;
    }

    /**
     * Begins a session for a store.
     *
     * <p>The arrival state starts at {@link ArrivalState#NO_FIX} rather than {@code OUTSIDE}: nothing has
     * been measured yet, so claiming the driver is outside the radius would be a guess. The first fix
     * replaces it within seconds.
     *
     * @param destination where the drive ends - the store's own coordinates
     */
    public void begin(long storeId, @NonNull GeoCoordinate destination) {
        activeStoreId = storeId;
        publish(new Snapshot(storeId, destination, ArrivalState.NO_FIX, Double.NaN));
    }

    /**
     * Publishes a new position reading for the active session.
     *
     * <p>Ignored when no session is running, so a late-arriving fix from a session that has just ended
     * cannot resurrect it. That race is real: the location provider delivers on the main thread, and
     * "End navigation" is also a main-thread tap, so the two can interleave.
     */
    public void update(@NonNull LocationFix fix, int arrivalRadiusMetres, long nowMillis) {
        Snapshot current = snapshot.getValue();
        if (current == null) {
            return;
        }

        ArrivalState state = com.cph.alpharoute.domain.ArrivalEvaluator.evaluate(
                fix, current.destination(), arrivalRadiusMetres, nowMillis);

        double distance = com.cph.alpharoute.domain.DistanceCalculator.distanceMetres(
                fix.latitude(), fix.longitude(),
                current.destination().latitude(), current.destination().longitude());

        publish(new Snapshot(current.storeId(), current.destination(), state, distance));
    }

    /**
     * Ends the session.
     *
     * <p>Emits {@code null}, which every observer reads as "no navigation is active". Called both by
     * "End navigation" and by the arrival being confirmed - so a completed store never leaves a session
     * pointing at itself.
     */
    public void end() {
        activeStoreId = NO_STORE;
        monitoring = false;
        publish(null);
    }

    /** Which store is active, or {@link #NO_STORE}. */
    public long activeStoreId() {
        return activeStoreId;
    }

    /** True while a session is running. */
    public boolean isActive() {
        return activeStoreId != NO_STORE;
    }

    /** The current snapshot, or {@code null}. For the one-off read a screen does when it opens. */
    @Nullable
    public Snapshot current() {
        return snapshot.getValue();
    }

    /**
     * Posts on the main thread.
     *
     * <p>{@code setValue} throws when called off the main thread, and the service may call this from a
     * location callback whose delivering thread is not guaranteed. {@code postValue} is safe either way,
     * and the small delay it can introduce is immaterial for a value that updates every few seconds.
     */
    private void publish(@Nullable Snapshot value) {
        snapshot.postValue(value);
    }
}
