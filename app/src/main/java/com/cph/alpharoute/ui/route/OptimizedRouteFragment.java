package com.cph.alpharoute.ui.route;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.MainActivity;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.AppSettingEntity;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.RoutePlanEntity;
import com.cph.alpharoute.data.db.RoutePlanStopEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.data.repository.WriteOutcome;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.OptimizationPlan;
import com.cph.alpharoute.domain.OptimizationStop;
import com.cph.alpharoute.domain.PendingStores;
import com.cph.alpharoute.domain.PlanMerge;
import com.cph.alpharoute.domain.StoreCandidate;
import com.cph.alpharoute.location.LocationFix;
import com.cph.alpharoute.navigation.NavigationLauncher;
import com.cph.alpharoute.navigation.OptimizationState;
import com.cph.alpharoute.util.RouteTextFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The route - every pending store, in the order to visit them (Phase 8.4, saved plans in Version 18).
 *
 * <p><b>What this screen is, and what it is not.</b> It answers "in what order should I go". It does not
 * guide the drive - that is still the Google Maps app (D-6/D-7) - and it does not replace the suggestion
 * card, which answers the smaller question "which one next".
 *
 * <p><b>The order can be the user's or the app's, and the user always wins.</b> The screen opens with an
 * order the planner worked out on the device, and every stop can be dragged to wherever the user wants it.
 * What is on screen can then be saved under a name and come back tomorrow. That is the whole of Version 18:
 * a solver asked the same question twice can answer differently, so "the plan" was never a thing the app
 * could promise - a saved plan is a decision the user made, and the app keeps it.
 *
 * <p><b>Nothing is saved until Save is pressed.</b> Dragging a stop changes the screen, not the database.
 * A screen that wrote on every drag would make an accidental nudge permanent, and there would be no way to
 * tell a deliberate arrangement from a mis-tap.
 *
 * <p><b>It says "optimised", never "shortest possible".</b> Nearest neighbour plus refinement is a
 * heuristic. It finds a good order, not provably the best one, and the distances it orders by are straight
 * lines over the globe rather than roads. Claiming more than that would be claiming something the app
 * cannot check - and once the user has arranged the route by hand, the screen is not claiming anything at
 * all, which is why the plan button says whose order it is.
 *
 * <p><b>No time is shown, because none was measured.</b> A duration needs a speed, and the app has never
 * measured how fast this driver drives. The screen omits the time and says the distances are estimates,
 * rather than filling the gap with a number it made up.
 */
public class OptimizedRouteFragment extends Fragment {

    /** Back-stack tag. */
    public static final String TAG = "OptimizedRouteFragment";

    /** "This order is not saved as a plan." Not a real id - Room starts at 1. */
    private static final long NO_PLAN = -1L;

    /** The menu ids that are not plans. Plan entries start at {@link #MENU_PLAN_BASE}. */
    private static final int MENU_NEW = 1;
    private static final int MENU_RENAME = 2;
    private static final int MENU_DELETE = 3;

    /**
     * Where the plan entries in the menu start.
     *
     * <p>Well clear of the three fixed ids above, so a plan can never be mistaken for an action however many
     * of them there are.
     */
    private static final int MENU_PLAN_BASE = 1000;

    /**
     * The menu group holding the saved plans.
     *
     * <p>Its own group, not {@code Menu.NONE}, because the group is what {@code setGroupCheckable} applies
     * to - and the three actions in the same menu must not come out tickable.
     */
    private static final int MENU_GROUP_PLANS = 1;

    /**
     * Where the chosen plan is remembered across a rotation.
     *
     * <p>Only the plan's id, not the order. A rotation reloads the plan from the database, so the order on
     * screen after turning the phone is the stored one - and an arrangement that was never saved is
     * correctly gone, because Save is the only thing that makes it real. Without this, turning the phone
     * would quietly switch the user back to the newest plan, which is the kind of thing that makes an app
     * feel like it is not listening.
     */
    private static final String STATE_PLAN_ID = "routePlanId";

    private RecyclerView list;
    private TextView summaryView;
    private TextView endpointsView;
    private TextView unassignedView;
    private TextView estimateView;
    private TextView reorderHintView;
    private TextView messageView;
    private LinearProgressIndicator loadingIndicator;
    private MaterialButton startButton;
    private MaterialButton recalculateButton;
    private MaterialButton planButton;
    private MaterialButton saveButton;

    private OptimizedRouteAdapter adapter;
    private ItemTouchHelper dragHelper;

    /** Every store, completed or not, so tapping any stop can find its row. */
    private final Map<Long, StoreEntity> storesById = new HashMap<>();

    /** The stores still to visit this round, keyed by id, so an order of ids can be turned into stops. */
    private final Map<Long, StoreCandidate> pendingById = new HashMap<>();

    /**
     * The order on screen.
     *
     * <p>The single source of truth for what the route is. The adapter renders it, dragging rearranges it,
     * and Save writes it out - so there is never a question of which of two lists is the real one.
     */
    private final List<StoreCandidate> orderedStops = new ArrayList<>();

    /**
     * The stores still to visit this round, in the stable id order.
     *
     * <p>Held as a list as well as a map because {@link PlanMerge} appends the stores a plan does not
     * mention <i>in the order it is given them</i> - so that order has to be a decided one, not whatever
     * order a {@code HashMap} happens to iterate in. A new store landing in a different place each time the
     * menu was opened would look like the app changing its mind.
     */
    private final List<StoreCandidate> roundPending = new ArrayList<>();

    /** Where the route starts and finishes, captured once per request so a drag measures the same route. */
    @Nullable
    private GeoCoordinate startPoint;
    @Nullable
    private GeoCoordinate homePoint;

    /** The round the plan belongs to, so a plan made for round 1 is not offered for round 2. */
    private int currentRound = 1;

    /** The plan the order on screen belongs to, or {@link #NO_PLAN} when it has never been saved. */
    private long currentPlanId = NO_PLAN;
    @Nullable
    private String currentPlanName;

    /** Whether the order on screen differs from what is stored. */
    private boolean dirty;

    /** The plan currently rendered, kept so "Start Route" knows where to go. */
    @Nullable
    private OptimizationPlan currentPlan;

    public OptimizedRouteFragment() {
        super(R.layout.fragment_optimized_route);
    }

    /** The screen. It takes no arguments - the plan is asked for when it opens. */
    @NonNull
    public static OptimizedRouteFragment newInstance() {
        return new OptimizedRouteFragment();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_PLAN_ID, currentPlanId);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            // Which plan the user was looking at, so a rotation does not silently switch them to another.
            currentPlanId = savedInstanceState.getLong(STATE_PLAN_ID, NO_PLAN);
        }

        list = view.findViewById(R.id.route_stops);
        summaryView = view.findViewById(R.id.tv_route_summary);
        endpointsView = view.findViewById(R.id.tv_route_endpoints);
        unassignedView = view.findViewById(R.id.tv_route_unassigned);
        estimateView = view.findViewById(R.id.tv_route_estimate);
        reorderHintView = view.findViewById(R.id.tv_route_reorder_hint);
        messageView = view.findViewById(R.id.tv_route_message);
        loadingIndicator = view.findViewById(R.id.route_loading);
        startButton = view.findViewById(R.id.btn_route_start);
        recalculateButton = view.findViewById(R.id.btn_route_recalculate);
        planButton = view.findViewById(R.id.btn_route_plan);
        saveButton = view.findViewById(R.id.btn_route_save);

        adapter = new OptimizedRouteAdapter(this::openStop, holder -> dragHelper.startDrag(holder));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        dragHelper = new ItemTouchHelper(new ReorderCallback());
        dragHelper.attachToRecyclerView(list);

        startButton.setOnClickListener(v -> startRoute());
        recalculateButton.setOnClickListener(v -> requestPlan());
        planButton.setOnClickListener(v -> showPlanMenu());
        saveButton.setOnClickListener(v -> savePlan());

        observePlan();

        // The position is OBSERVED even though this screen only reads it once per request. LiveData's
        // getValue() is null until something observes it, and a route built without a start point would
        // begin somewhere the user is not. Observing here costs nothing and keeps currentCoordinate()
        // honest.
        AlphaRouteApp.from(requireContext())
                .locationProvider()
                .currentFix()
                .observe(getViewLifecycleOwner(), fix -> { /* read on demand */ });

        requestPlan();
    }

    // ------------------------------------------------------------------ the request

    /**
     * Works out a plan.
     *
     * <p><b>Three one-shot reads, then the ordering.</b> They are one-shot rather than observed because a
     * plan is a snapshot of a moment: a route calculated from a store list that changed halfway through
     * would describe a set of shops the user no longer has.
     *
     * <p><b>Why this is not {@code observeAll().getValue()}.</b> That was the original implementation and
     * it was a bug waiting to happen: {@code LiveData.getValue()} returns {@code null} until something
     * observes it, and this screen observes nothing. It appeared to work only because the Dashboard
     * happens to observe the same repositories first - so the moment the app was restored straight to
     * this screen (process death with the back stack rebuilt) the reads would return null and the screen
     * would claim there was nothing left to visit. A read that depends on another screen having run is
     * not a read.
     */
    private void requestPlan() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(context);

        app.storeRepository().findAllNow(stores ->
                app.completionRepository().findAllNow(history ->
                        app.settingsRepository().getNow(settings -> {
                            if (!isAdded()) {
                                // The screen was closed while the reads were in flight.
                                return;
                            }
                            planFrom(app, stores, history, settings);
                        })));
    }

    /** Builds the plan from the three reads. Runs on the main thread, with all three in hand. */
    private void planFrom(@NonNull AlphaRouteApp app,
                          @Nullable List<StoreEntity> stores,
                          @Nullable List<CompletionHistoryEntity> history,
                          @Nullable AppSettingEntity settings) {

        List<StoreCandidate> pending = pendingStores(stores, history, settings);

        roundPending.clear();
        roundPending.addAll(pending);

        startPoint = currentCoordinate(app);
        homePoint = homeCoordinate(settings);
        currentRound = settings == null ? 1 : settings.currentRound;

        if (pending.isEmpty()) {
            clearRoute();
            showMessage(getString(R.string.route_no_stores_pending));
            return;
        }

        // A saved plan for this round wins over a fresh suggestion: it is the order the user decided, and
        // recomputing over the top of it would throw that decision away every time the screen opened.
        app.planRepository().findPlansInRoundNow(currentRound, plans -> {
            if (!isAdded()) {
                return;
            }
            if (plans == null || plans.isEmpty()) {
                suggestOrder(app, pending);
                return;
            }
            loadPlan(app, planToShow(plans), pending);
        });
    }

    /**
     * Which plan to put on screen.
     *
     * <p>The one that was showing before a rotation, when there was one and it still exists; otherwise the
     * newest. Falls back rather than failing, because a plan can be deleted from the menu while the screen
     * is in the background.
     */
    @NonNull
    private RoutePlanEntity planToShow(@NonNull List<RoutePlanEntity> plans) {
        for (RoutePlanEntity plan : plans) {
            if (plan.id == currentPlanId) {
                return plan;
            }
        }
        return plans.get(0);
    }

    /**
     * Asks for an order the app worked out, as an unsaved draft.
     *
     * <p>No request, no API key, no daily allowance - and therefore nothing here that can come back empty
     * because the network was down, which is what the screen used to do.
     */
    private void suggestOrder(@NonNull AlphaRouteApp app, @NonNull List<StoreCandidate> pending) {
        currentPlanId = NO_PLAN;
        currentPlanName = null;
        dirty = false;
        app.routingRepository().planRoute(pending, startPoint, homePoint);
    }

    /**
     * Puts a saved plan back together with the stores that are still left, and shows it.
     *
     * <p>{@link PlanMerge} does the joining, and it is pure and tested because all three of its jobs are
     * silent when they go wrong: a completed store must drop out, a newly added one must appear, and a plan
     * that lists the same store twice must not visit it twice.
     */
    private void loadPlan(@NonNull AlphaRouteApp app,
                          @NonNull RoutePlanEntity plan,
                          @NonNull List<StoreCandidate> pending) {

        app.planRepository().findStopsNow(plan.id, stops -> {
            if (!isAdded()) {
                return;
            }
            if (stops == null) {
                // The read failed. A fresh suggestion is a better answer than an empty screen.
                suggestOrder(app, pending);
                return;
            }

            List<Long> savedOrder = new ArrayList<>(stops.size());
            for (RoutePlanStopEntity stop : stops) {
                savedOrder.add(stop.storeId);
            }

            List<StoreCandidate> merged = PlanMerge.merge(savedOrder, pending);

            currentPlanId = plan.id;
            currentPlanName = plan.name;
            dirty = false;
            app.routingRepository().measureRoute(merged, startPoint, homePoint);
        });
    }

    /**
     * The stores still to visit, in a stable order.
     *
     * <p>Sorted by id so two runs over the same data are identical. The order sent to the planner does not
     * decide the answer - it orders the set itself - but an unstable input would make two identical routes
     * look different in a log.
     *
     * <p>The round rule itself is {@code PendingStores}, which is pure and tested - the Dashboard's
     * suggestion needs the same answer, and writing it twice would let the two drift.
     */
    @NonNull
    private List<StoreCandidate> pendingStores(@Nullable List<StoreEntity> stores,
                                               @Nullable List<CompletionHistoryEntity> history,
                                               @Nullable AppSettingEntity settings) {
        storesById.clear();
        pendingById.clear();

        if (stores == null || stores.isEmpty()) {
            return Collections.emptyList();
        }

        int round = settings == null ? 1 : settings.currentRound;
        Set<Long> completed = PendingStores.idsPendingInRound(history, round);

        List<StoreCandidate> pending = new ArrayList<>();
        for (StoreEntity store : stores) {
            if (store == null) {
                continue;
            }
            storesById.put(store.id, store);
            if (PendingStores.isPending(store.id, completed)) {
                StoreCandidate candidate = new StoreCandidate(
                        store.id, store.storeCode, store.storeName,
                        store.latitude, store.longitude);
                pending.add(candidate);
                pendingById.put(store.id, candidate);
            }
        }

        Collections.sort(pending, (left, right) -> Long.compare(left.id(), right.id()));
        return pending;
    }

    // ------------------------------------------------------------------ the answer

    /** Watches the route state. The plan is a single value, so a plain observer is enough. */
    private void observePlan() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context)
                .routingRepository()
                .observeOptimization()
                .observe(getViewLifecycleOwner(), this::render);
    }

    private void render(@Nullable OptimizationState state) {
        if (state == null || summaryView == null) {
            return;
        }

        if (state.isReady()) {
            loadingIndicator.setVisibility(View.GONE);
            renderPlan(state.plan());
            return;
        }

        // IDLE is "nothing has been asked for yet", and LOADING is "the answer is on its way". Neither is a
        // failure, and this screen is now reached before its first request has finished - so reporting IDLE
        // as a failure would flash "could not build a route" on every single open, on a screen that always
        // has a route to show.
        if (state.isLoading() || state.status() == OptimizationState.Status.IDLE) {
            // Keep whatever is on screen while a recalculation runs, so the list does not blink out.
            loadingIndicator.setVisibility(View.VISIBLE);
            return;
        }

        loadingIndicator.setVisibility(View.GONE);
        clearRoute();
        showMessage(failureText(state));
    }

    /** Empties the screen. Used both for a failure and for "there is nothing left to visit". */
    private void clearRoute() {
        currentPlan = null;
        orderedStops.clear();
        adapter.submit(Collections.emptyList());
        summaryView.setText(R.string.route_title);
        endpointsView.setVisibility(View.GONE);
        unassignedView.setVisibility(View.GONE);
        estimateView.setVisibility(View.GONE);
        startButton.setEnabled(false);
        updatePlanBar();
    }

    /**
     * The failure, with the service's own reason attached when there is one.
     *
     * <p><b>The detail is appended only when it exists.</b> Several statuses carry none at all - "not
     * configured", "too many stops", "quota used up" - and appending unconditionally rendered those as a
     * sentence with an empty pair of parentheses hanging off the end.
     *
     * <p>When it does exist it is worth showing, because it is the only thing that separates failures the
     * user's next move differs between: a rejected key, an exhausted quota, and a coordinate the solver
     * could not place all arrive looking the same otherwise.
     */
    @NonNull
    private String failureText(@NonNull OptimizationState state) {
        String message = messageFor(state.status());
        String detail = state.detail();
        return detail == null || detail.isEmpty() ? message : message + "\n\n(" + detail + ")";
    }

    private void renderPlan(@Nullable OptimizationPlan plan) {
        if (plan == null) {
            clearRoute();
            showMessage(getString(R.string.route_failed));
            return;
        }

        currentPlan = plan;
        syncOrderFromPlan(plan);
        messageView.setVisibility(View.GONE);

        if (!plan.hasStops()) {
            summaryView.setText(R.string.route_title);
            endpointsView.setVisibility(View.GONE);
            unassignedView.setVisibility(View.GONE);
            estimateView.setVisibility(View.GONE);
            startButton.setEnabled(false);
            updatePlanBar();
            showMessage(getString(R.string.route_empty_plan));
            return;
        }

        summaryView.setText(summaryText(plan));

        endpointsView.setText(endpointsText(plan));
        endpointsView.setVisibility(View.VISIBLE);

        // The distances are straight lines over the globe, not roads, so the screen says so rather than
        // letting the totals imply a road measurement that was never taken. Shown only for a plan that
        // carries no driving time - which is every route made on the device, and the honest signal that
        // this one was not measured on a road network.
        estimateView.setVisibility(plan.totalDurationSeconds() > 0L ? View.GONE : View.VISIBLE);

        // Only when it is not zero. A route that quietly leaves shops out is worse than one that says so,
        // because the user would drive home believing the day was done.
        unassignedView.setVisibility(plan.unassignedCount() > 0 ? View.VISIBLE : View.GONE);
        if (plan.unassignedCount() > 0) {
            unassignedView.setText(getResources().getQuantityString(
                    R.plurals.route_unassigned, plan.unassignedCount(), plan.unassignedCount()));
        }

        adapter.submit(rowsFor(plan));
        startButton.setEnabled(true);
        updatePlanBar();
    }

    /**
     * Rebuilds the on-screen order from the plan that is being rendered.
     *
     * <p>The plan is the thing the user is looking at, so if the two ever disagreed the screen would be
     * showing an order it does not believe in. Rebuilding from the plan makes that impossible - and it is
     * how a freshly suggested order gets into {@link #orderedStops} in the first place, since the planner
     * returns the sequence it chose rather than the one it was given.
     *
     * <p>Applied only when every stop resolved to a store. A partial rebuild would silently shorten the
     * route, which is exactly the failure this screen exists to prevent.
     */
    private void syncOrderFromPlan(@NonNull OptimizationPlan plan) {
        List<StoreCandidate> fromPlan = new ArrayList<>(plan.stopCount());
        for (OptimizationStop stop : plan.stops()) {
            StoreCandidate candidate = pendingById.get(stop.storeId());
            if (candidate != null) {
                fromPlan.add(candidate);
            }
        }

        if (fromPlan.size() == plan.stopCount()) {
            orderedStops.clear();
            orderedStops.addAll(fromPlan);
        }
    }

    /** Joins each stop with its store, so the row can show a name rather than an id. */
    @NonNull
    private List<OptimizedRouteAdapter.Row> rowsFor(@NonNull OptimizationPlan plan) {
        List<OptimizedRouteAdapter.Row> rows = new ArrayList<>(plan.stopCount());
        for (OptimizationStop stop : plan.stops()) {
            StoreEntity store = storesById.get(stop.storeId());
            rows.add(new OptimizedRouteAdapter.Row(
                    stop,
                    store == null ? null : store.routeNumber,
                    store == null ? null : store.storeCode,
                    store == null ? null : store.storeName));
        }
        return rows;
    }

    /**
     * "12 stops · 34.2 km", and the driving time only when there is one.
     *
     * <p><b>The time is dropped rather than filled in.</b> A route made on the device has no duration - the
     * planner has no speed to work from - and rendering that as "0 s" would state something the app never
     * measured. The three-part form is kept for a service-planned route, which does know a real driving
     * time, so the summary stays correct whichever produced the plan.
     */
    @NonNull
    private String summaryText(@NonNull OptimizationPlan plan) {
        String distance =
                RouteTextFormatter.distanceText((int) Math.round(plan.totalDistanceMetres()));

        long seconds = plan.totalDurationSeconds();
        if (seconds <= 0L) {
            return getString(R.string.route_summary_format_no_time, plan.stopCount(), distance);
        }

        return getString(
                R.string.route_summary_format,
                plan.stopCount(),
                distance,
                // Clamped before the cast: a route of a few hours is a few tens of thousands of seconds,
                // nowhere near the limit, but an unclamped cast of a corrupt value would go negative and
                // render as a nonsense duration.
                RouteTextFormatter.durationText((int) Math.min(Integer.MAX_VALUE, seconds)));
    }

    /**
     * Where the route begins and ends, in words.
     *
     * <p>The user gave the app a position and a Home; a route that silently ignored either would be a
     * surprise. Both halves are named only when they are true.
     */
    @NonNull
    private String endpointsText(@NonNull OptimizationPlan plan) {
        if (plan.startsAtCurrentLocation() && plan.endsAtHome()) {
            return getString(R.string.route_endpoints_both);
        }
        if (plan.endsAtHome()) {
            return getString(R.string.route_endpoints_home);
        }
        if (plan.startsAtCurrentLocation()) {
            return getString(R.string.route_endpoints_location);
        }
        return getString(R.string.route_endpoints_neither);
    }

    /** The message for a failed state, each naming the user's actual next move. */
    @NonNull
    private String messageFor(@NonNull OptimizationState.Status status) {
        switch (status) {
            case NOT_CONFIGURED:
                return getString(R.string.route_not_configured);
            case TOO_MANY_STOPS:
                return getString(R.string.route_too_many_stops);
            case OFFLINE:
                return getString(R.string.route_offline);
            case QUOTA_EXHAUSTED:
                return getString(R.string.route_quota);
            case IDLE:
            case LOADING:
            case READY:
            case FAILED:
            default:
                return getString(R.string.route_failed);
        }
    }

    private void showMessage(@NonNull String message) {
        messageView.setText(message);
        messageView.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ reordering

    /**
     * Drag-to-reorder, on the vertical axis only.
     *
     * <p><b>Long-press anywhere on a row, or press the grip.</b> Both, because the grip tells a user who
     * has never seen this screen that the order is theirs to set, and the long-press is what a user who
     * already knows will reach for.
     *
     * <p><b>Swipe is deliberately off.</b> There is nothing a sideways swipe could sensibly do here, and an
     * accidental one would look like a deleted stop.
     */
    private final class ReorderCallback extends ItemTouchHelper.SimpleCallback {

        ReorderCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {

            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from < 0 || to < 0 || from == to || from >= orderedStops.size()
                    || to >= orderedStops.size()) {
                return false;
            }

            // Both lists are swapped the same way, so they stay in step. The re-render on drop rebuilds
            // the adapter's rows from the order entirely, so even a missed move cannot leave them apart.
            Collections.swap(orderedStops, from, to);
            adapter.move(from, to);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            // Swipe is not enabled - see the class note.
        }

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                // Hides every leg distance while a row is in the air. A leg describes the step from the
                // stop before it, and that step has just stopped being true.
                adapter.setReordering(true);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if (adapter.isReordering()) {
                adapter.setReordering(false);
                onOrderChangedByHand();
            }
        }
    }

    /**
     * Re-measures and redraws after the user has moved a stop.
     *
     * <p><b>Re-measured, not just redrawn.</b> Every leg distance on screen was calculated for the order
     * the route used to have, so after a drag they all describe journeys that are no longer planned. They
     * are recomputed here, by the same arithmetic the planner uses - which is why a hand-made order and a
     * suggested one show distances that can be compared.
     */
    private void onOrderChangedByHand() {
        Context context = getContext();
        if (context == null || orderedStops.isEmpty()) {
            return;
        }

        // The screen now differs from the database. Nothing is written until Save is pressed - an
        // accidental nudge must not become permanent, and a deliberate arrangement must be distinguishable
        // from a mis-tap.
        dirty = true;
        AlphaRouteApp.from(context).routingRepository()
                .measureRoute(orderedStops, startPoint, homePoint);
    }

    // ------------------------------------------------------------------ the plan bar

    /**
     * Updates the plan button and the Save button.
     *
     * <p><b>Save is off when there is nothing to save.</b> A button that is always live invites the tap
     * that does nothing, and gives no signal that the arrangement on screen is already stored. It is on
     * when the order has never been saved, and when it has been saved but changed since.
     */
    private void updatePlanBar() {
        if (planButton == null || saveButton == null) {
            return;
        }

        if (currentPlanId == NO_PLAN) {
            planButton.setText(R.string.route_plan_draft);
        } else if (dirty) {
            planButton.setText(getString(
                    R.string.route_plan_unsaved_format,
                    currentPlanName == null ? getString(R.string.route_plan_draft) : currentPlanName));
        } else {
            // Never null while a plan is current, but a null here would render an empty button rather than
            // throwing, and an empty button is a worse bug than a visible fallback.
            planButton.setText(currentPlanName == null ? getString(R.string.route_plan_draft)
                    : currentPlanName);
        }

        boolean hasStops = !orderedStops.isEmpty();
        saveButton.setEnabled(hasStops && (currentPlanId == NO_PLAN || dirty));

        // The hint is only true when there is more than one stop - a single stop cannot be moved anywhere.
        reorderHintView.setVisibility(orderedStops.size() > 1 ? View.VISIBLE : View.GONE);
    }

    /**
     * The menu behind the plan button: every saved plan for this round, then the actions.
     *
     * <p>Built at runtime because the list of plans is data, and a menu resource cannot hold data. The
     * current plan is ticked, so the menu answers "which one am I looking at" as well as "what else is
     * there".
     *
     * <p>Rename and Delete appear only when the order on screen belongs to a plan. Acting on a plan the
     * user cannot see is how the wrong one gets deleted.
     */
    private void showPlanMenu() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(context);

        app.planRepository().findPlansInRoundNow(currentRound, plans -> {
            if (!isAdded() || planButton == null) {
                return;
            }

            List<RoutePlanEntity> saved =
                    plans == null ? Collections.emptyList() : plans;

            PopupMenu popup = new PopupMenu(requireContext(), planButton);
            Menu menu = popup.getMenu();

            for (int i = 0; i < saved.size(); i++) {
                RoutePlanEntity plan = saved.get(i);
                MenuItem item = menu.add(MENU_GROUP_PLANS, MENU_PLAN_BASE + i, i, plan.name);
                item.setCheckable(true);
                item.setChecked(plan.id == currentPlanId);
            }

            menu.add(Menu.NONE, MENU_NEW, MENU_PLAN_BASE, R.string.route_plan_menu_new);

            if (currentPlanId != NO_PLAN) {
                menu.add(Menu.NONE, MENU_RENAME, MENU_PLAN_BASE + 1, R.string.route_plan_menu_rename);
                menu.add(Menu.NONE, MENU_DELETE, MENU_PLAN_BASE + 2, R.string.route_plan_menu_delete);
            }

            // Exclusive, so the tick moves rather than accumulating. Only the plans group - the actions
            // are in Menu.NONE and must stay untickable.
            menu.setGroupCheckable(MENU_GROUP_PLANS, true, true);

            popup.setOnMenuItemClickListener(item -> onPlanMenuChoice(app, item.getItemId(), saved));
            popup.show();
        });
    }

    /** Handles a menu choice. Returns true when the choice was consumed. */
    private boolean onPlanMenuChoice(@NonNull AlphaRouteApp app,
                                     int itemId,
                                     @NonNull List<RoutePlanEntity> saved) {

        if (itemId >= MENU_PLAN_BASE) {
            int index = itemId - MENU_PLAN_BASE;
            if (index >= saved.size()) {
                return false;
            }
            RoutePlanEntity chosen = saved.get(index);
            if (chosen.id == currentPlanId) {
                // Already looking at it. Re-reading would throw away an unsaved arrangement for nothing.
                return true;
            }
            dirty = false;
            loadPlan(app, chosen, roundPending);
            return true;
        }

        switch (itemId) {
            case MENU_NEW:
                // A fresh suggestion, with no name behind it - so Save offers to keep it.
                suggestOrder(app, roundPending);
                return true;
            case MENU_RENAME:
                promptRename(app);
                return true;
            case MENU_DELETE:
                confirmDelete(app);
                return true;
            default:
                return false;
        }
    }

    /** Renames the plan on screen. */
    private void promptRename(@NonNull AlphaRouteApp app) {
        if (currentPlanId == NO_PLAN || currentPlanName == null) {
            return;
        }

        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.route_plan_rename_hint);
        input.setText(currentPlanName);
        input.setSelection(currentPlanName.length());

        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(requireContext());
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.route_plan_rename_title)
                .setView(container)
                .setNegativeButton(R.string.route_plan_cancel, null)
                .setPositiveButton(R.string.route_plan_save,
                        (dialog, which) -> renameTo(app, input.getText().toString()))
                .show();
    }

    private void renameTo(@NonNull AlphaRouteApp app, @NonNull String name) {
        final long planId = currentPlanId;
        app.planRepository().renamePlan(planId, name, outcome -> {
            if (!isAdded()) {
                return;
            }
            if (outcome == WriteOutcome.SUCCESS) {
                currentPlanName = name.trim();
                updatePlanBar();
            } else if (outcome == WriteOutcome.INVALID_DATA) {
                showSnack(R.string.route_plan_name_invalid);
            } else {
                showSnack(R.string.route_plan_gone);
            }
        });
    }

    /**
     * Deletes the plan on screen.
     *
     * <p>The confirmation names the plan and says what is <i>not</i> affected, because "delete" on a screen
     * full of store names is frightening - and the stores really are untouched.
     */
    private void confirmDelete(@NonNull AlphaRouteApp app) {
        if (currentPlanId == NO_PLAN || currentPlanName == null) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.route_plan_delete_title)
                .setMessage(getString(R.string.route_plan_delete_message, currentPlanName))
                .setNegativeButton(R.string.route_plan_cancel, null)
                .setPositiveButton(R.string.route_plan_delete_confirm,
                        (dialog, which) -> deleteCurrentPlan(app))
                .show();
    }

    private void deleteCurrentPlan(@NonNull AlphaRouteApp app) {
        final long planId = currentPlanId;
        app.planRepository().deletePlan(planId, outcome -> {
            if (!isAdded()) {
                return;
            }
            if (outcome == WriteOutcome.FAILED) {
                showSnack(R.string.route_plan_gone);
                return;
            }

            // The order that was on screen came from the plan that no longer exists, so it is no longer
            // anything the app can stand behind. A fresh suggestion is the honest replacement.
            dirty = false;
            suggestOrder(app, roundPending);
            showSnack(R.string.route_plan_deleted);
        });
    }

    // ------------------------------------------------------------------ saving

    /**
     * Writes the order on screen into the current plan, or into a new one.
     *
     * <p><b>Creating and updating are different, and the difference is visible.</b> With no plan behind the
     * order, Save creates one and the name is generated by the repository ("Plan 1", "Plan 2") - counting
     * on the disk thread so two plans cannot take the same default name however fast the button is pressed.
     */
    private void savePlan() {
        if (currentPlan == null || orderedStops.isEmpty()) {
            showSnack(R.string.route_plan_nothing_to_save);
            return;
        }

        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(context);
        final OptimizationPlan order = currentPlan;

        if (currentPlanId == NO_PLAN) {
            app.planRepository().createPlan(currentRound, null, order, saved -> {
                if (!isAdded()) {
                    return;
                }
                if (saved == null) {
                    showSnack(R.string.route_plan_save_failed);
                    return;
                }
                currentPlanId = saved.id;
                currentPlanName = saved.name;
                dirty = false;
                updatePlanBar();
                showSnack(R.string.route_plan_saved_format, saved.name);
            });
            return;
        }

        app.planRepository().replacePlanStops(currentPlanId, order, outcome -> {
            if (!isAdded()) {
                return;
            }
            if (outcome == WriteOutcome.SUCCESS) {
                dirty = false;
                updatePlanBar();
                showSnack(R.string.route_plan_updated);
            } else if (outcome == WriteOutcome.NOT_FOUND) {
                showSnack(R.string.route_plan_gone);
            } else {
                showSnack(R.string.route_plan_save_failed);
            }
        });
    }

    private void showSnack(int stringRes) {
        showSnack(stringRes, null);
    }

    private void showSnack(int stringRes, @Nullable Object formatArg) {
        if (!isAdded()) {
            return;
        }
        String message = formatArg == null
                ? getString(stringRes)
                : getString(stringRes, formatArg);
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ the actions

    /**
     * Hands the first stop of the route to the navigation app.
     *
     * <p><b>Only the first stop.</b> The app cannot do turn-by-turn (D-6/D-7), so "start the route" means
     * "drive to the first one" - and the route screen is still there to say where the second one is. Handing
     * the whole list to Google Maps would need waypoints, which is a different hand-off and is not this
     * phase.
     *
     * <p>Since the order can be arranged by hand, "the first stop" is now genuinely the user's choice.
     */
    private void startRoute() {
        if (currentPlan == null || !currentPlan.hasStops() || !isAdded()) {
            return;
        }

        StoreEntity first = storesById.get(currentPlan.stops().get(0).storeId());
        if (first == null) {
            Snackbar.make(requireView(), R.string.route_stop_missing_store_snackbar,
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        boolean launched = NavigationLauncher.launch(requireContext(), first.latitude, first.longitude);
        if (!launched) {
            Snackbar.make(requireView(), R.string.nav_no_navigation_app, Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Opens one stop.
     *
     * <p>The map, centred on that store - the same jump the Completion Detail screen makes, through the
     * same channel. The route is a list of places; tapping one should show it.
     */
    private void openStop(@NonNull OptimizedRouteAdapter.Row row) {
        if (!isAdded()) {
            return;
        }
        StoreEntity store = storesById.get(row.storeId);
        if (store == null || !(requireActivity() instanceof MainActivity)) {
            return;
        }
        ((MainActivity) requireActivity()).showMapAt(
                new GeoCoordinate(store.latitude, store.longitude));
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    private static GeoCoordinate currentCoordinate(@NonNull AlphaRouteApp app) {
        LocationFix fix = app.locationProvider().currentFix().getValue();
        if (fix == null || !fix.hasUsableCoordinates()) {
            return null;
        }
        return new GeoCoordinate(fix.latitude(), fix.longitude());
    }

    @Nullable
    private static GeoCoordinate homeCoordinate(@Nullable AppSettingEntity settings) {
        if (settings == null || !settings.isHomeSet()) {
            return null;
        }
        return new GeoCoordinate(settings.homeLatitude, settings.homeLongitude);
    }
}
