package com.cph.alpharoute.ui.dashboard;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.data.db.AppSettingEntity;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.PendingStores;
import com.cph.alpharoute.domain.RoadDistanceStrategy;
import com.cph.alpharoute.domain.RouteSuggestionStrategy;
import com.cph.alpharoute.domain.StoreCandidate;
import com.cph.alpharoute.domain.SuggestionContext;
import com.cph.alpharoute.domain.SuggestionResult;
import com.cph.alpharoute.domain.TrackerCalculator;
import com.cph.alpharoute.domain.TrackerSnapshot;
import com.cph.alpharoute.location.LocationFix;
import com.cph.alpharoute.util.DateKeyHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Supplies the Dashboard with one consistent set of figures, and with the next-store suggestion.
 *
 * <p><b>Why a {@code MediatorLiveData} rather than observers in the Fragment.</b> Every Dashboard figure is
 * computed from <i>both</i> tables - the percentage needs the store count and the completion count
 * together. If the Fragment observed them separately it would recompute twice per change, and in between
 * it would briefly show a set of numbers that never existed. The mediator joins the sources and emits once,
 * after both have been applied.
 *
 * <p><b>The suggestion recomputes itself, which is Phase 7.2.</b> It is driven by the same four sources, so
 * a completion recorded anywhere in the app produces a new suggestion with no wiring between the completion
 * dialog and this card. There is no "recalculate" button because there is nothing to press it for.
 *
 * <p><b>Why it is an {@code AndroidViewModel}.</b> The repositories live on the Application, so the
 * ViewModel needs the Application to reach them. This is the one case where the plain {@code ViewModel}
 * would not do, and it costs nothing: the default factory supplies the Application automatically.
 */
public class DashboardViewModel extends AndroidViewModel {

    /**
     * The joined tracker result.
     *
     * <p>Starts with no value: the first emission arrives when Room's first query returns, which is a
     * fraction of a second. The screen shows its loading state until then rather than inventing zeros -
     * "0 completed" would be a claim about the data, and it would be wrong.
     */
    private final MediatorLiveData<TrackerSnapshot> tracker = new MediatorLiveData<>();

    /** The suggestion, recomputed on the same triggers. */
    private final MutableLiveData<SuggestionResult> suggestion = new MutableLiveData<>();

    /** The last emission from each source. All four are needed before a figure can be computed. */
    private List<StoreEntity> stores = Collections.emptyList();
    private List<CompletionHistoryEntity> history = Collections.emptyList();
    private int currentRound = 1;
    @Nullable
    private GeoCoordinate currentLocation;
    @Nullable
    private GeoCoordinate home;

    /**
     * The store the user picked by hand, or {@code null} when the strategy is choosing.
     *
     * <p><b>Deliberately not persisted.</b> A manual override is a decision about this moment - "not that
     * one, this one" - and it is cleared as soon as the store stops being pending. Storing it would need a
     * schema change and would mean an override from three days ago still steering the app.
     *
     * <p>Held in the ViewModel rather than the Fragment, so it survives a rotation. A rotation is not a new
     * decision.
     */
    @Nullable
    private Long overriddenStoreId;

    /**
     * The rule in use.
     *
     * <p>{@link RoadDistanceStrategy}, which <b>delegates down a chain</b> so one object covers 7.1, 7.3
     * and 8.2:
     *
     * <ol>
     *   <li>road distances are known → nearest by real driving distance (8.2);</li>
     *   <li>they are not → {@code HomeAwareStrategy}, which balances the way home (7.3);</li>
     *   <li>no Home either → {@code NearestStrategy}, straight-line (7.1).</li>
     * </ol>
     *
     * <p>That chain is why the app works offline without a special case anywhere: the network only ever
     * improves the answer, it is never required for one.
     */
    private final RouteSuggestionStrategy strategy = new RoadDistanceStrategy();

    // ------------------------------------------------------------------ road distances (8.2)

    /**
     * Driving distances by store id, or empty when they are not known.
     *
     * <p>Empty is the ordinary state offline, and it is not an error - the strategies fall back to straight
     * lines, which is exactly the Phase 7 behaviour.
     */
    private Map<Long, Double> roadDistances = Collections.emptyMap();

    /** Which set of stores the current {@link #roadDistances} was fetched for. */
    private String roadDistanceSignature = "";

    /** Where the user was when they were fetched, so a stale set can be spotted. */
    @Nullable
    private GeoCoordinate roadDistanceOrigin;

    /**
     * How far the user must move before the driving distances are worth asking for again.
     *
     * <p><b>This is what stops the app burning its quota.</b> The position updates every few seconds, and
     * without a threshold every fix would be a matrix request - the free plan allows 500 a day, which a
     * single drive would exhaust in under an hour. 250 m is roughly the point at which the nearest store
     * could plausibly have changed.
     */
    private static final double ROAD_DISTANCE_REFRESH_METRES = 250.0;

    /** Whether a matrix request is in flight, so a second one is not started on top of it. */
    private boolean roadDistanceRequestInFlight;

    public DashboardViewModel(@NonNull Application application) {
        super(application);

        AlphaRouteApp app = AlphaRouteApp.from(application);

        tracker.addSource(app.storeRepository().observeAll(), emitted -> {
            stores = emitted == null ? Collections.emptyList() : emitted;
            recompute();
        });

        tracker.addSource(app.completionRepository().observeAllOrdered(), emitted -> {
            history = emitted == null ? Collections.emptyList() : emitted;
            recompute();
        });

        // The round. Observed because "Start new round" changes it, and the Dashboard's Remaining,
        // Completion and Round cards all move when it does.
        tracker.addSource(app.settingsRepository().observe(), settings -> {
            applySettings(settings);
            recompute();
        });

        // The position. It changes every few seconds while the user is moving, and the suggestion depends
        // on it - so the card follows the driver rather than being fixed when the screen opened.
        tracker.addSource(app.locationProvider().currentFix(), fix -> {
            currentLocation = coordinateOf(fix);
            recompute();
        });
    }

    /** The figures to show. Emits again whenever either table or the round changes. */
    @NonNull
    public LiveData<TrackerSnapshot> tracker() {
        return tracker;
    }

    /** The next store to visit. Emits on the same triggers as {@link #tracker()}. */
    @NonNull
    public LiveData<SuggestionResult> suggestion() {
        return suggestion;
    }

    /**
     * The pending stores, nearest first, for the "Choose another" list.
     *
     * <p>Ordered by distance from the user when there is a fix, so the list the user picks from is in the
     * order they would expect. Without a fix it falls back to the store order, which is stable rather than
     * meaningful - and the list says nothing about distance in that case.
     */
    @NonNull
    public List<StoreCandidate> pendingCandidates() {
        return pendingInRound();
    }

    /**
     * Overrides the suggestion with a store the user chose.
     *
     * <p>Ignored when the id is not one of the pending stores, so a stale tap cannot pin the card to a
     * store that has since been completed or deleted.
     */
    public void chooseStore(long storeId) {
        for (StoreCandidate candidate : pendingInRound()) {
            if (candidate.id() == storeId) {
                overriddenStoreId = storeId;
                recomputeSuggestion();
                return;
            }
        }
    }

    /** Returns the card to the strategy's own choice. */
    public void clearOverride() {
        overriddenStoreId = null;
        recomputeSuggestion();
    }

    /**
     * Whether Home has been configured.
     *
     * <p>Answered from the settings this ViewModel already holds, rather than by the Fragment reading
     * {@code settingsRepository().observe().getValue()}. That read looked harmless and was not: LiveData's
     * {@code getValue()} is null until something observes it, and the Fragment's first render can arrive
     * from the <i>stores</i> source before the settings row has ever been delivered - in which case the
     * round-complete card would offer to take the user home when Home was set all along.
     *
     * <p>The ViewModel is the one that observes settings, so it is the one that can answer this.
     */
    public boolean hasHome() {
        return home != null;
    }

    /** Home's position, or {@code null} when it has not been set. */
    @Nullable
    public GeoCoordinate homeCoordinate() {
        return home;
    }

    // ------------------------------------------------------------------ recomputation

    /**
     * Recomputes everything from the cached emissions.
     *
     * <p>{@code setValue}, not {@code postValue}: Room's {@code LiveData} delivers on the main thread, and
     * {@code MediatorLiveData.setValue} requires it. Posting would work too but would defer every update by
     * a message-loop turn for no benefit.
     */
    private void recompute() {
        tracker.setValue(TrackerCalculator.compute(
                stores,
                history,
                DateKeyHelper.today(),
                DateKeyHelper.yesterday(),
                currentRound));

        recomputeSuggestion();
    }

    /**
     * Recomputes the suggestion.
     *
     * <p>The override is re-checked against the current pending list on every pass, so a store that has
     * just been completed or deleted silently drops the override rather than leaving the card pointing at
     * something that is no longer there.
     */
    private void recomputeSuggestion() {
        List<StoreCandidate> pending = pendingInRound();

        // Asked for before the suggestion is computed, so the next recomputation - the one this triggers -
        // can use them. This call returns immediately and never blocks.
        maybeRefreshRoadDistances(pending);

        if (overriddenStoreId != null) {
            for (StoreCandidate candidate : pending) {
                if (candidate.id() == overriddenStoreId) {
                    // The user's choice stands until it stops being valid. The distance is measured from
                    // the current position so the card still shows how far it is.
                    suggestion.setValue(SuggestionResult.suggested(
                            candidate,
                            SuggestionResult.Reason.NEAREST,
                            distanceTo(candidate)));
                    return;
                }
            }
            // It is no longer pending - completed, undone into a different round, or deleted.
            overriddenStoreId = null;
        }

        SuggestionContext context = new SuggestionContext(
                pending,
                countRealStores(),
                currentLocation,
                home,
                roadDistances);

        suggestion.setValue(strategy.suggest(context));
    }

    // ------------------------------------------------------------------ fetching driving distances

    /**
     * Asks the routing service for real driving distances, when it is worth asking.
     *
     * <p><b>Three conditions, and each one protects the daily allowance:</b>
     *
     * <ol>
     *   <li><b>The set of pending stores must have changed</b>, or the user must have moved far enough to
     *       matter. Without this, every GPS fix would be a request.</li>
     *   <li><b>Nothing else may be in flight.</b> A second request on top of the first is a request the
     *       first answer would immediately invalidate.</li>
     *   <li><b>There must be a position to measure from.</b> The matrix needs an origin.</li>
     * </ol>
     *
     * <p>A failed request is not retried here. It leaves the distances empty, the strategies fall back to
     * straight lines, and the next time the user moves far enough or the store list changes, it is tried
     * again - which is the right retry policy for a service with a daily cap.
     */
    private void maybeRefreshRoadDistances(@NonNull List<StoreCandidate> pending) {
        if (currentLocation == null || pending.isEmpty()) {
            // Nothing to measure from or nothing to measure to. Any distances already held are now stale,
            // because the pending set has changed shape.
            roadDistances = Collections.emptyMap();
            roadDistanceSignature = "";
            return;
        }

        if (roadDistanceRequestInFlight) {
            return;
        }

        String signature = signatureOf(pending);
        if (signature.equals(roadDistanceSignature) && !hasMovedFarEnough()) {
            // Already have distances for this exact set of stores, from roughly here.
            return;
        }

        roadDistanceRequestInFlight = true;
        roadDistanceSignature = signature;
        roadDistanceOrigin = currentLocation;

        List<GeoCoordinate> destinations = new ArrayList<>(pending.size());
        for (StoreCandidate candidate : pending) {
            destinations.add(candidate.coordinate());
        }

        final GeoCoordinate origin = currentLocation;

        AlphaRouteApp.from(getApplication())
                .routingRepository()
                .loadDrivingDistances(origin, destinations, distances -> {
                    roadDistanceRequestInFlight = false;

                    if (distances == null || distances.length != pending.size()) {
                        // The service could not answer, or answered for a different set. Falling back is
                        // silent by design - the suggestion still appears, with straight-line distances.
                        roadDistances = Collections.emptyMap();
                        return;
                    }

                    Map<Long, Double> mapped = new HashMap<>();
                    for (int i = 0; i < pending.size(); i++) {
                        if (!Double.isNaN(distances[i])) {
                            mapped.put(pending.get(i).id(), distances[i]);
                        }
                    }

                    if (mapped.isEmpty()) {
                        roadDistances = Collections.emptyMap();
                        return;
                    }

                    roadDistances = mapped;
                    // Recompute now that real distances are in. This is the one place the suggestion is
                    // driven by something other than a database change.
                    recomputeSuggestion();
                });
    }

    /**
     * Whether the user has moved far enough for the driving distances to be worth re-fetching.
     *
     * <p>Distance rather than "has the fix changed at all": GPS jitters by a few metres even when the phone
     * is on a table, and treating that as movement would spend the daily allowance on noise.
     */
    private boolean hasMovedFarEnough() {
        if (roadDistanceOrigin == null || currentLocation == null) {
            return true;
        }
        double moved = com.cph.alpharoute.domain.DistanceCalculator.distanceMetres(
                roadDistanceOrigin, currentLocation);
        return Double.isNaN(moved) || moved >= ROAD_DISTANCE_REFRESH_METRES;
    }

    /**
     * A stable identity for a set of pending stores.
     *
     * <p>Built from the ids in list order, which is already sorted nearest-first when there is a position.
     * Two different sets never share a signature, so a stale set of distances can never be used for the
     * wrong stores - which would pair a distance with a shop it does not belong to.
     */
    @NonNull
    private static String signatureOf(@NonNull List<StoreCandidate> pending) {
        StringBuilder signature = new StringBuilder(pending.size() * 8);
        for (StoreCandidate candidate : pending) {
            signature.append(candidate.id()).append(',');
        }
        return signature.toString();
    }

    /**
     * The stores still to visit in the round in progress.
     *
     * <p>The round rule itself lives in {@code PendingStores}, which is pure and tested - the optimized
     * route screen needs the same answer, and writing it twice would let the two drift. The symptom would
     * be a suggestion pointing at a store the map shows as done.
     */
    @NonNull
    private List<StoreCandidate> pendingInRound() {
        if (stores.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> completedThisRound = PendingStores.idsPendingInRound(history, currentRound);

        List<StoreCandidate> pending = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (StoreEntity store : stores) {
            if (store == null || !seen.add(store.id)) {
                continue;
            }
            if (!PendingStores.isPending(store.id, completedThisRound)) {
                continue;
            }
            pending.add(new StoreCandidate(
                    store.id, store.storeCode, store.storeName, store.latitude, store.longitude));
        }

        // Nearest first, when there is something to measure from. Sorted here rather than in the strategy
        // because this list is also the "Choose another" list, which is a UI concern.
        if (currentLocation != null) {
            Collections.sort(pending, (left, right) -> {
                double leftDistance = distanceTo(left);
                double rightDistance = distanceTo(right);
                if (Double.isNaN(leftDistance)) {
                    return Double.isNaN(rightDistance) ? Long.compare(left.id(), right.id()) : 1;
                }
                if (Double.isNaN(rightDistance)) {
                    return -1;
                }
                if (leftDistance != rightDistance) {
                    return Double.compare(leftDistance, rightDistance);
                }
                return Long.compare(left.id(), right.id());
            });
        }

        return pending;
    }

    /** How many real stores exist, so the strategy can tell "no stores" from "round finished". */
    private int countRealStores() {
        int count = 0;
        for (StoreEntity store : stores) {
            if (store != null) {
                count++;
            }
        }
        return count;
    }

    private double distanceTo(@NonNull StoreCandidate candidate) {
        if (currentLocation == null) {
            return Double.NaN;
        }
        return com.cph.alpharoute.domain.DistanceCalculator.distanceMetres(
                currentLocation, candidate.coordinate());
    }

    private void applySettings(@Nullable AppSettingEntity settings) {
        currentRound = settings == null ? 1 : settings.currentRound;
        home = settings != null && settings.isHomeSet()
                ? new GeoCoordinate(settings.homeLatitude, settings.homeLongitude)
                : null;
    }

    @Nullable
    private static GeoCoordinate coordinateOf(@Nullable LocationFix fix) {
        if (fix == null || !fix.hasUsableCoordinates()) {
            return null;
        }
        return new GeoCoordinate(fix.latitude(), fix.longitude());
    }
}
