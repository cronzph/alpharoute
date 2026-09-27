package com.cph.alpharoute.ui.route;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.data.repository.RoutingRepository;
import com.cph.alpharoute.domain.RoutePlan;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.location.LocationFix;
import com.cph.alpharoute.location.LocationProvider;
import com.cph.alpharoute.navigation.NavigationLauncher;
import com.cph.alpharoute.navigation.RouteState;
import com.cph.alpharoute.navigation.RoutingConfig;
import com.cph.alpharoute.util.RouteTextFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

/**
 * Route Preview - screen 7 of the Phase 0.3 screen plan. What the drive looks like, before anything
 * navigational happens.
 *
 * <p>Opened from <b>Get directions</b> on the Store Details sheet. It shows the store, the distance, the
 * estimated time and the attribution, and it draws the route line - except that it does not draw the line
 * itself: the Map tab does, because the Map tab owns the {@code MapView} and osmdroid is confined to
 * {@code MapMarkerController} (AD-8). This sheet asks the routing repository for a route; the map observes
 * the same repository and draws it. Neither has a reference to the other.
 *
 * <p><b>Navigation never starts on its own.</b> Tapping <b>Start navigation</b> is the only thing that
 * hands off, and it hands off to the Google Maps app rather than guiding the driver itself - that is
 * D-6/D-7, and it is the only $0 route to live traffic.
 *
 * <p><b>Nothing here invents a route.</b> While the request is in flight the line is not drawn, and if the
 * request fails the sheet says so and the map stays empty. A line drawn from a partial answer would look
 * plausible and be wrong, and a driver following it is worse off than one who was told to try again.
 */
public class RoutePreviewBottomSheet extends BottomSheetDialogFragment {

    /** Dialog tag, so the map can check whether the sheet is already showing. */
    public static final String TAG = "RoutePreviewBottomSheet";

    private static final String ARG_STORE_ID = "arg_store_id";

    /** A store id that cannot exist - Room's autoGenerate primary key starts at 1. */
    private static final long NO_STORE_ID = -1L;

    private long storeId = NO_STORE_ID;

    /** Where the drive ends. Resolved once, from the store, and never changed while the sheet is open. */
    @Nullable
    private GeoCoordinate destination;

    /**
     * The store the route is for.
     *
     * <p>Held so that starting navigation can hand it to the session manager, which needs the store id
     * and not only its coordinates. It is the row exactly as Room returned it, taken once when the sheet
     * opened - not re-read on the tap, because by then the user has already seen which store they are
     * driving to and a mid-flight rename would make the confirmation disagree with the action.
     */
    @Nullable
    private StoreEntity store;

    private LinearProgressIndicator loadingIndicator;
    private TextView messageView;
    private View summaryGroup;
    private TextView storeCodeView;
    private TextView storeNameView;
    private TextView distanceView;
    private TextView durationView;
    private MaterialButton retryButton;
    private MaterialButton startButton;

    private RoutingRepository routingRepository;
    private LocationProvider locationProvider;

    public RoutePreviewBottomSheet() {
        super(R.layout.sheet_route_preview);
    }

    /**
     * Creates the sheet for one store.
     *
     * <p>The id travels in the arguments rather than a field, because FragmentManager recreates fragments
     * through the no-argument constructor - a field would be lost on rotation and the sheet would come
     * back with no destination.
     */
    public static RoutePreviewBottomSheet newInstance(long storeId) {
        RoutePreviewBottomSheet sheet = new RoutePreviewBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_STORE_ID, storeId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            storeId = args.getLong(ARG_STORE_ID, NO_STORE_ID);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        loadingIndicator = view.findViewById(R.id.route_preview_loading);
        messageView = view.findViewById(R.id.tv_route_preview_message);
        summaryGroup = view.findViewById(R.id.group_route_preview_summary);
        storeCodeView = view.findViewById(R.id.tv_route_preview_store_code);
        storeNameView = view.findViewById(R.id.tv_route_preview_store_name);
        distanceView = view.findViewById(R.id.tv_route_preview_distance);
        durationView = view.findViewById(R.id.tv_route_preview_duration);
        retryButton = view.findViewById(R.id.btn_route_preview_retry);
        startButton = view.findViewById(R.id.btn_route_preview_start);

        // The attribution comes from RoutingConfig rather than strings.xml, for the same reason the
        // OpenStreetMap attribution lives in MapConfig: it is provider metadata that has to match the
        // service beside it. It is a licence condition, not decoration.
        TextView sourceView = view.findViewById(R.id.tv_route_preview_source);
        sourceView.setText(
                getString(R.string.route_preview_source_format, RoutingConfig.attribution()));

        routingRepository = AlphaRouteApp.from(requireContext()).routingRepository();
        locationProvider = AlphaRouteApp.from(requireContext()).locationProvider();

        MaterialButton cancelButton = view.findViewById(R.id.btn_route_preview_cancel);
        cancelButton.setOnClickListener(v -> dismiss());
        retryButton.setOnClickListener(v -> requestRoute());
        startButton.setOnClickListener(v -> onStartNavigationClicked());

        observeState();
        observeLocation();

        loadStore();
    }

    // ------------------------------------------------------------------ the destination

    private void loadStore() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (storeId == NO_STORE_ID) {
            closeWithMessage(R.string.store_missing);
            return;
        }
        AlphaRouteApp.from(context).storeRepository().findById(storeId, this::onStoreLoaded);
    }

    private void onStoreLoaded(@Nullable StoreEntity store) {
        if (!isAdded()) {
            return;
        }
        if (store == null) {
            // Deleted while the details sheet was open, or by a sync. There is nowhere to route to.
            closeWithMessage(R.string.store_missing);
            return;
        }

        destination = new GeoCoordinate(store.latitude, store.longitude);
        this.store = store;

        storeCodeView.setText(getString(
                R.string.store_summary_pair_format, store.routeNumber, store.storeCode));
        storeNameView.setText(getString(
                R.string.store_summary_pair_format, store.storeName, store.area));

        requestRoute();
    }

    // ------------------------------------------------------------------ the route

    /**
     * Asks the routing repository for the route, or reports why it cannot be asked for.
     *
     * <p>The origin is always the current position and the destination is always the store, as the screen
     * plan requires. When there is no usable fix the request is not sent at all - it would have no origin
     * - and the state carries "Waiting for your location before routing." instead.
     */
    private void requestRoute() {
        if (destination == null || routingRepository == null) {
            return;
        }

        LocationFix fix = locationProvider.currentFix().getValue();
        if (fix == null || !fix.hasUsableCoordinates()) {
            routingRepository.calculateRoute(null, destination);
            return;
        }

        routingRepository.calculateRoute(
                new GeoCoordinate(fix.latitude(), fix.longitude()), destination);
    }

    private void observeState() {
        routingRepository.observeState().observe(getViewLifecycleOwner(), this::render);
    }

    /**
     * Asks again the moment a fix arrives, but only while we are still waiting for one.
     *
     * <p>Without this the sheet would sit on "Waiting for your location" until the user closed and
     * reopened it, which is a poor way to say "give it a few seconds".
     */
    private void observeLocation() {
        locationProvider.currentFix().observe(getViewLifecycleOwner(), fix -> {
            if (destination == null || fix == null || !fix.hasUsableCoordinates()) {
                return;
            }
            RouteState current = routingRepository.observeState().getValue();
            if (current != null && current.status() == RouteState.Status.NO_LOCATION) {
                requestRoute();
            }
        });
    }

    // ------------------------------------------------------------------ rendering

    /** Brings the whole sheet in line with one state. One place, so no two parts can disagree. */
    private void render(@Nullable RouteState state) {
        if (state == null) {
            return;
        }

        boolean ready = state.isReady() && state.plan() != null;

        // IDLE counts as loading here: the sheet has just opened and the request is a moment away, so
        // showing an error or a blank panel in that instant would be wrong both ways.
        boolean working = state.isLoading() || state.status() == RouteState.Status.IDLE;

        loadingIndicator.setVisibility(working ? View.VISIBLE : View.GONE);

        messageView.setVisibility(ready ? View.GONE : View.VISIBLE);
        messageView.setText(messageFor(state.status()));

        summaryGroup.setVisibility(ready ? View.VISIBLE : View.GONE);

        if (ready) {
            RoutePlan plan = state.plan();
            distanceView.setText(RouteTextFormatter.distanceText(plan.distanceMetres()));
            durationView.setText(RouteTextFormatter.durationText(plan.durationSeconds()));
        }

        // A button that cannot do anything is not drawn. Start navigation needs a real route; Retry is
        // only offered where retrying could actually help.
        startButton.setVisibility(ready ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(isRetryable(state.status()) ? View.VISIBLE : View.GONE);
    }

    @StringRes
    private static int messageFor(@NonNull RouteState.Status status) {
        switch (status) {
            case NO_LOCATION:
                return R.string.route_preview_no_location;
            case NOT_CONFIGURED:
                return R.string.route_preview_not_configured;
            case OFFLINE:
                return R.string.route_preview_offline;
            case QUOTA_EXHAUSTED:
                return R.string.route_preview_quota;
            case FAILED:
                return R.string.route_preview_failed;
            case IDLE:
            case LOADING:
            case READY:
            default:
                // READY hides the message entirely, so only the two working states are ever seen here.
                return R.string.route_preview_loading;
        }
    }

    /**
     * Whether Retry is worth offering.
     *
     * <p>Not for a used-up daily quota - retrying now cannot succeed, and a button that fails again is
     * worse than no button. Not for a missing key, which needs a rebuild rather than a tap. Not for a
     * missing fix, which resolves itself the moment one arrives.
     */
    private static boolean isRetryable(@NonNull RouteState.Status status) {
        return status == RouteState.Status.OFFLINE || status == RouteState.Status.FAILED;
    }

    // ------------------------------------------------------------------ navigation

    private void onStartNavigationClicked() {
        if (destination == null || store == null) {
            return;
        }

        Context context = getContext();
        if (context == null) {
            return;
        }

        boolean launched = NavigationLauncher.launch(
                context, destination.latitude(), destination.longitude());

        if (!launched) {
            // Neither Google Maps nor Waze is installed. Say so rather than dismissing and leaving the
            // user wondering why nothing happened.
            //
            // The session is deliberately NOT begun here. A session means "this app is watching for your
            // arrival", and with no navigation app there is no drive to watch. Starting one would leave a
            // permanent notification on a phone that is going nowhere.
            Snackbar.make(requireView(), R.string.route_preview_no_navigation_app,
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        // The hand-off succeeded, so the drive is real and Phase 4.3 begins monitoring it. This is the
        // only place a session starts: every entry point is a tap on this button, which is also what
        // makes the foreground service legal to start - Android 12+ forbids beginning one from the
        // background, and this runs from a visible screen.
        AlphaRouteApp.from(context)
                .navigationSessionManager()
                .start(context, store, null);

        // A navigation app is now in front of us. This sheet has done its job, and closing it also drops
        // the route line - see onDestroy.
        dismiss();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // Covered by onDestroy for the ordinary cases; this keeps the route from lingering for the moment
        // between the dialog closing and the fragment going.
        releaseRouteIfLeaving();
    }

    @Override
    public void onDestroy() {
        releaseRouteIfLeaving();
        super.onDestroy();
    }

    /**
     * Drops the route, but only when this sheet is genuinely going away.
     *
     * <p><b>A rotation also destroys and rebuilds this fragment</b>, so clearing on every destroy would
     * throw away a perfectly good route and spend another request from the free plan's daily allowance
     * just to redraw it. {@code isRemoving()} is what tells the two apart.
     */
    private void releaseRouteIfLeaving() {
        if (routingRepository == null) {
            return;
        }
        boolean activityGone = getActivity() == null || getActivity().isFinishing();
        if (isRemoving() || activityGone) {
            // The line belongs to this screen. Closing it must not leave a route drawn on the map that
            // nothing is explaining any more.
            routingRepository.clearRoute();
        }
    }

    // ------------------------------------------------------------------ messages

    private void closeWithMessage(@StringRes int messageRes) {
        Context context = getContext();
        if (context != null) {
            // A Toast rather than a Snackbar: this sheet is being dismissed in the same breath, and a
            // Snackbar is anchored to the view it was shown on.
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show();
        }
        dismiss();
    }
}
