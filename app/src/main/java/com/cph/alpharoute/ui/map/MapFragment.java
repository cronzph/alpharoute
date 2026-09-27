package com.cph.alpharoute.ui.map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.RecoveryState;
import com.cph.alpharoute.location.LocationFix;
import com.cph.alpharoute.location.LocationPermissionHelper;
import com.cph.alpharoute.location.LocationProvider;
import com.cph.alpharoute.navigation.NavigationSession;
import com.cph.alpharoute.navigation.NavigationSessionManager;
import com.cph.alpharoute.ui.route.NavigationActiveBottomSheet;
import com.cph.alpharoute.ui.store.AddEditStoreBottomSheet;
import com.cph.alpharoute.ui.store.AddFromLinkBottomSheet;
import com.cph.alpharoute.ui.store.StoreDetailBottomSheet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;

import java.util.Collections;
import java.util.List;

/**
 * The Map tab - an OpenStreetMap view with the user's current position on it.
 *
 * <p>Phase 3.1 drew the map. Phase 3.2 adds the location permission flow, the blue dot with its
 * accuracy circle, and the states that go with them: permission not yet granted, permission refused
 * for good, location services switched off, approximate-only permission, no GPS fix yet, and no
 * connection.
 *
 * <p><b>The camera is never forced to follow the user.</b> That is a requirement of this phase, not a
 * preference. The map centres on the first fix so the user is not left staring at the wrong part of the
 * country, and after that it moves only when the user taps "My location". A map that re-centres itself
 * while you are trying to look at something is a map you cannot use.
 *
 * <p><b>One banner, not five.</b> Every problem above is "something is stopping the map from being
 * useful", and they overlap - a user with no permission may also have no connection. So there is a
 * single card whose message and button change with {@link BannerState}, and one place decides which
 * problem has priority.
 */
public class MapFragment extends Fragment {

    private static final String TAG = "AlphaRouteMap";

    /**
     * Saved so a rotation does not re-centre the map and throw away wherever the user had panned to.
     */
    private static final String STATE_CENTRED_ON_FIRST_FIX = "state_centred_on_first_fix";

    /**
     * Set once the cold-start resume prompt has been considered, so a rotation does not offer it again.
     *
     * <p>Not saved in {@code onSaveInstanceState}: a configuration change is not a new launch, but a
     * process restart is - and a process restart is exactly when the question is worth asking. Losing the
     * flag on a real relaunch is therefore the behaviour we want, not a bug to guard against.
     */
    private boolean recoveryChecked;

    /**
     * Which problem the banner is currently reporting, in priority order.
     *
     * <p>Ordered from "you cannot use the map at all" down to "the map works but is degraded". Only one
     * is ever shown.
     */
    private enum BannerState {
        NONE,
        PERMISSION_RATIONALE,
        PERMISSION_DENIED_PERMANENTLY,
        GPS_DISABLED,
        PERMISSION_APPROXIMATE,
        OFFLINE
    }

    // ------------------------------------------------------------------ views

    private MapView mapView;
    private LinearProgressIndicator loadingIndicator;
    private MaterialCardView bannerCard;
    private TextView bannerMessage;
    private MaterialButton bannerAction;
    private TextView statusView;
    private FloatingActionButton myLocationButton;
    private MaterialCardView navBarCard;
    private TextView navBarTitle;

    // ------------------------------------------------------------------ state

    private CurrentLocationOverlay locationOverlay;
    private MapMarkerController markerController;
    private LocationProvider locationProvider;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean centredOnFirstFix;

    /**
     * The store list exactly as Room last emitted it.
     *
     * <p>Not a cache of the database - a copy of one emission, kept only so the navigation bar can name
     * the store it is driving to without a second query. It is replaced wholesale on every emission, so
     * it cannot drift from what the map is drawing.
     */
    private List<StoreEntity> currentStores = Collections.emptyList();

    /**
     * Registered as a field, because {@code registerForActivityResult} must happen before the fragment
     * is created. The registry itself is only resolved when {@code launch()} is called, so this is safe
     * even though the fragment is not attached yet.
     */
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    granted -> {
                        // The map contents are ignored on purpose: what the system now reports is the
                        // authoritative answer, and it is the only thing that also covers a partial
                        // grant such as "approximate only". Re-evaluating handles every case.
                        Log.d(TAG, "Location permission result: " + granted);
                        onLocationStateMaybeChanged();
                    });

    public MapFragment() {
        super(R.layout.fragment_map);
    }

    // ------------------------------------------------------------------ setup

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            centredOnFirstFix = savedInstanceState.getBoolean(STATE_CENTRED_ON_FIRST_FIX, false);
        }

        mapView = view.findViewById(R.id.map_view);
        loadingIndicator = view.findViewById(R.id.map_loading);
        bannerCard = view.findViewById(R.id.card_map_banner);
        bannerMessage = view.findViewById(R.id.tv_map_banner_message);
        bannerAction = view.findViewById(R.id.btn_map_banner_action);
        statusView = view.findViewById(R.id.tv_map_status);
        myLocationButton = view.findViewById(R.id.fab_my_location);
        navBarCard = view.findViewById(R.id.card_nav_bar);
        navBarTitle = view.findViewById(R.id.tv_nav_bar_title);

        locationProvider = AlphaRouteApp.from(requireContext()).locationProvider();
        locationOverlay = new CurrentLocationOverlay(mapView);
        markerController = new MapMarkerController(mapView);

        // Tapping a store marker opens its details. Wired here rather than inside the controller, so the
        // controller stays a map component that knows nothing about screens - and receives a plain store
        // id instead of an osmdroid event.
        markerController.setOnStoreClickListener(this::openStoreDetailSheet);

        // The other way to add a store: from a pasted Google Maps link. It sits beside the long-press
        // rather than replacing it - a long-press is for a shop you are standing in front of, and a link
        // is for one you found on Google Maps.
        view.findViewById(R.id.fab_add_from_link)
                .setOnClickListener(v -> openAddFromLinkSheet());

        // Phase 4.3 - the whole bar opens the Navigation Active sheet. The bar itself has no action of
        // its own: it is a status line and a way back to the sheet, and giving it a second button would
        // make a two-word strip look like a control panel.
        navBarCard.setOnClickListener(v -> openNavigationSheet());
        view.findViewById(R.id.btn_nav_bar_open).setOnClickListener(v -> openNavigationSheet());

        TextView attribution = view.findViewById(R.id.tv_map_attribution);
        attribution.setText(MapConfig.attribution());

        configureMap();
        addLongPressHandler();
        observeStores();
        observeHome();
        observeRoute();
        observePlaceLink();
        observeNavigation();
        hideLoadingWhenLaidOut(view);
        observeNetworkState();
        observeLocation();
        observeGpsState();
        checkForResumableSession();

        myLocationButton.setOnClickListener(v -> centreOnCurrentLocation(true));
    }

    /**
     * Adds the long-press handler that opens the Add Store form.
     *
     * <p><b>The overlay goes in at index 0, and that placement matters.</b> osmdroid dispatches events to
     * overlays from the top down, so an overlay at index 0 is the bottom of the stack: markers added
     * later sit above it and receive their taps first. A long-press on empty map still reaches this
     * handler, while a tap on a store marker will not be swallowed by it - which is what Phase 3.5 needs.
     */
    private void addLongPressHandler() {
        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                // Not handled here, and not by this overlay at all: a tap on a store marker is handled
                // by the marker's own click listener (see MapMarkerController), which is why the markers
                // sit above this overlay. Returning false lets anything underneath react as well.
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                openAddStoreSheet(point);
                // True means "consumed", so nothing underneath also reacts to the press.
                return true;
            }
        });

        mapView.getOverlays().add(0, eventsOverlay);
    }

    /**
     * Watches every store in the database and keeps the markers in step with it.
     *
     * <p><b>This replaced the single-store callback that Phase 3.3 used</b>, and the replacement is
     * strictly better in three ways:
     *
     * <ol>
     *   <li><b>Restart.</b> The first emission carries everything already saved, so markers come back
     *       after a rotation or a cold start. Phase 3.3 could only draw the store it had just been told
     *       about, which is why its markers vanished.</li>
     *   <li><b>Refresh.</b> Room re-runs the query whenever the {@code stores} table changes, so a store
     *       completed in Phase 5 turns green on the map with no extra wiring.</li>
     *   <li><b>Deletion.</b> A store removed from the table simply stops being in the list, and
     *       {@code renderAll} prunes its marker. Nothing has to remember to tell the map.</li>
     * </ol>
     *
     * <p>The store list is also the only thing the map reads. It never queries by id and never holds a
     * copy, so Room stays the single source of truth (AD-4) and a marker can only ever show what was
     * actually saved.
     */
    private void observeStores() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context)
                .storeRepository()
                .observeAll()
                .observe(getViewLifecycleOwner(), stores -> {
                    // Kept so the navigation bar can name the store it is driving to without a second
                    // query. Replaced wholesale, so it always matches what the markers are showing.
                    currentStores = stores == null ? Collections.emptyList() : stores;

                    if (markerController != null) {
                        markerController.renderAll(currentStores);
                    }
                });
    }

    /**
     * Watches the settings row and keeps the Home pin in step with it.
     *
     * <p>Home lives in the settings table rather than the stores table, so it needs its own observation -
     * {@link #observeStores} will never carry it. Because it is the same LiveData pattern, setting Home
     * on the Home Setup screen makes the pin appear on the map with nothing else wired up.
     *
     * <p><b>Home is never counted as a store.</b> It goes to its own pin rather than to
     * {@code renderAll}, so the marker count and every dashboard total are unaffected by it - see
     * {@code MapMarkerController}.
     */
    private void observeHome() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context)
                .settingsRepository()
                .observe()
                .observe(getViewLifecycleOwner(), settings -> {
                    if (markerController == null) {
                        return;
                    }
                    // isHomeSet() is the single place that decides whether Home is configured - it
                    // requires the flag and both coordinates to agree. Re-checking the raw fields here
                    // would be a second definition of the same rule, free to disagree with the first.
                    if (settings != null && settings.isHomeSet()) {
                        markerController.renderHome(settings.homeLatitude, settings.homeLongitude);
                    } else {
                        markerController.renderHome(null, null);
                    }
                });
    }

    /**
     * Watches the routing repository and keeps the route line in step with it.
     *
     * <p>The map owns the line because the map owns the {@code MapView}, and osmdroid is confined to
     * {@code MapMarkerController} (AD-8). The Route Preview sheet asks for the route and the map draws it;
     * neither has a reference to the other, and nothing is passed between fragments.
     *
     * <p><b>Only a real route is drawn.</b> While a request is in flight - or after it failed - any line
     * still on the map would be the <i>previous</i> route, left over from an earlier request and shown as
     * though it were the answer to the one just made. So anything that is not
     * {@link com.cph.alpharoute.navigation.RouteState.Status#READY} clears the line. That is also the
     * screen plan's "the polyline is not drawn until a real route arrives".
     */
    private void observeRoute() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context)
                .routingRepository()
                .observeState()
                .observe(getViewLifecycleOwner(), state -> {
                    if (markerController == null || state == null) {
                        return;
                    }
                    if (state.isReady() && state.plan() != null) {
                        markerController.renderRoute(state.plan().points());
                    } else {
                        markerController.clearRoute();
                    }
                });
    }

    /**
     * Watches the navigation session and shows or hides the compact bar.
     *
     * <p>The bar's title is the store's code, read from the store list the map already observes - not from
     * Room again. {@link #observeStores} has every store in memory, and looking one up here would be a
     * second query for a value that is already on hand. The list is Room's own emission, so it is still
     * the single source of truth (AD-4); this is reading it, not keeping a copy.
     *
     * <p><b>No session, no bar.</b> A session ends by itself in one case - the driver reaching the radius
     * stops the monitoring - so the bar has to be able to disappear without a tap.
     */
    private void observeNavigation() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context)
                .navigationSession()
                .observe()
                .observe(getViewLifecycleOwner(), snapshot -> {
                    if (navBarCard == null) {
                        return;
                    }
                    if (snapshot == null) {
                        navBarCard.setVisibility(View.GONE);
                        return;
                    }

                    navBarCard.setVisibility(View.VISIBLE);
                    navBarTitle.setText(getString(
                            R.string.nav_bar_title_format, labelFor(snapshot.storeId())));
                });
    }

    /**
     * The store's code and name for the bar, looked up in the map's own store list.
     *
     * <p>Falls back to the id when the store cannot be found - which happens in the moment between a
     * store being deleted and the next session ending. Showing the id is worse than showing a name and
     * better than showing a blank bar.
     */
    @NonNull
    private String labelFor(long storeId) {
        for (StoreEntity store : currentStores) {
            if (store.id == storeId) {
                return store.routeNumber + " — " + store.storeName;
            }
        }
        return "#" + storeId;
    }

    /**
     * Opens the Navigation Active sheet, unless it is already open.
     *
     * <p>The same guard the other sheets use: two copies of the same sheet is a state the user cannot
     * reason about.
     */
    private void openNavigationSheet() {
        if (!isAdded()) {
            return;
        }
        if (getParentFragmentManager().findFragmentByTag(NavigationActiveBottomSheet.TAG) != null) {
            return;
        }
        NavigationActiveBottomSheet.newInstance()
                .show(getParentFragmentManager(), NavigationActiveBottomSheet.TAG);
    }

    // ------------------------------------------------------------------ cold-start recovery

    /**
     * Asks whether a recorded session is worth offering back, and offers it if so.
     *
     * <p><b>Run once per launch, not once per view.</b> {@code recoveryChecked} stops a rotation from
     * re-asking - the dialog would otherwise reappear every time the screen is turned. It is deliberately
     * not saved to {@code savedInstanceState}: within one process the answer cannot have changed, but a
     * fresh process is a fresh launch and the question is legitimate again.
     *
     * <p><b>The check never starts the service.</b> {@link NavigationSessionManager} reports the state and
     * this only shows a dialog; the service is started from the user's tap, which is the only way Android
     * 12+ allows it. If the prompt is dismissed or ignored, nothing is broken - the session simply is not
     * resumed, and the stale reference is cleared from Room so it cannot be offered again.
     *
     * <p>A dead session is also how the app avoids a ghost: {@code lastSelectedStoreId} is written when
     * navigation starts, and a phone that was switched off mid-drive leaves it behind. Four hours is where
     * {@code NavigationRecovery} draws the line between "probably still driving" and "forgotten".
     */
    private void checkForResumableSession() {
        if (recoveryChecked) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }

        recoveryChecked = true;
        AlphaRouteApp.from(context).navigationSessionManager().checkForResumableSession(state -> {
            // Two things can have happened while the disk read ran: the view may be gone, or the user may
            // have started a session by themselves. Either way the prompt is wrong now, so it is dropped.
            if (state != RecoveryState.OFFER_RESUME || !isAdded() || getView() == null) {
                return;
            }
            if (AlphaRouteApp.from(requireContext()).navigationSession().isActive()) {
                return;
            }
            showResumeDialog();
        });
    }

    /**
     * Asks the driver whether to pick the drive back up.
     *
     * <p>The store's name is put in the message, so the question is answerable without opening anything.
     * It is looked up in the map's own store list - the same list {@link #observeNavigation} reads - so
     * this costs no second query and cannot disagree with what is on the map. If the store is not in the
     * list, the dialog still appears showing the id: an offered resume that cannot name the destination is
     * still better than silently dropping a real drive.
     */
    private void showResumeDialog() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        NavigationSessionManager manager =
                AlphaRouteApp.from(context).navigationSessionManager();

        // Read the id first, from the same table the check just read. This is only for the label - the
        // authoritative read happens inside resume(), which re-reads the store and tolerates it being gone.
        manager.findResumableStore(storeId -> {
            if (!isAdded() || getView() == null || storeId == NavigationSession.NO_STORE) {
                return;
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.nav_resume_title)
                    .setMessage(getString(R.string.nav_resume_message, labelFor(storeId)))
                    .setNegativeButton(R.string.nav_resume_discard, (dialog, which) -> discardSession())
                    .setPositiveButton(R.string.nav_resume_action, (dialog, which) -> resumeSession())
                    .show();
        });
    }

    /** Picks the offered drive back up. The tap is what starts the service - see the class note on AD-13. */
    private void resumeSession() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context).navigationSessionManager().resume(context, store -> {
            if (store == null) {
                // The store was deleted, or the reference went missing, between the check and the tap.
                // Saying so is the honest outcome: a resume that silently did nothing would look like a
                // broken button.
                View root = getView();
                if (root != null) {
                    Snackbar.make(root, R.string.nav_resume_failed, Snackbar.LENGTH_LONG).show();
                }
                return;
            }
            openNavigationSheet();
        });
    }

    /** Declines the offered drive and clears the reference, so it is not offered again next launch. */
    private void discardSession() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        AlphaRouteApp.from(context).navigationSessionManager().stop(context);
    }

    private void openAddStoreSheet(@Nullable GeoPoint point) {
        if (point == null || !isAdded()) {
            return;
        }
        // Ignore a second long-press while the form is already open, rather than stacking two sheets.
        if (getParentFragmentManager().findFragmentByTag(AddEditStoreBottomSheet.TAG) != null) {
            return;
        }

        AddEditStoreBottomSheet sheet = AddEditStoreBottomSheet.newInstance(
                point.getLatitude(), point.getLongitude());
        sheet.show(getParentFragmentManager(), AddEditStoreBottomSheet.TAG);
    }

    /**
     * Opens the "Add from a link" sheet.
     *
     * <p>Ignored while it is already open, rather than stacking two copies of the same sheet.
     */
    private void openAddFromLinkSheet() {
        if (!isAdded()) {
            return;
        }
        if (getParentFragmentManager().findFragmentByTag(AddFromLinkBottomSheet.TAG) != null) {
            return;
        }
        AddFromLinkBottomSheet.newInstance()
                .show(getParentFragmentManager(), AddFromLinkBottomSheet.TAG);
    }

    /**
     * Moves the camera to a location read from a pasted link.
     *
     * <p><b>Why the map cares about a store that does not exist yet.</b> A store imported from a link is
     * usually somewhere else entirely - that is the whole reason to import it rather than long-press. So
     * without this the user would paste a link, save the store, and see absolutely nothing happen, with no
     * obvious way to find the pin they just added.
     *
     * <p>The camera is otherwise only ever moved when the user asks for it. This is them asking: they
     * pasted a location, and the map is expected to show it.
     *
     * <p>A rotation re-delivers the last state to the new view, so the camera is moved again. That is
     * harmless - it is the same point, and the sheet is still open over it.
     */
    private void observePlaceLink() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context)
                .placeLinkRepository()
                .observe()
                .observe(getViewLifecycleOwner(), state -> {
                    if (mapView == null || state == null || !state.isFound()) {
                        return;
                    }
                    GeoCoordinate coordinate = state.coordinate();
                    if (coordinate == null) {
                        return;
                    }

                    mapView.getController().animateTo(
                            new GeoPoint(coordinate.latitude(), coordinate.longitude()));
                    if (mapView.getZoomLevelDouble() < MapConfig.LOCATION_ZOOM) {
                        mapView.getController().setZoom(MapConfig.LOCATION_ZOOM);
                    }
                });
    }

    /**
     * Opens the Store Details sheet for a tapped marker.
     *
     * <p><b>Only the id crosses over.</b> The sheet reads the store back from Room itself, for the same
     * reason the map never holds a copy of a store: Room is the single source of truth (AD-4), so a
     * sheet fed from a snapshot taken when the marker was drawn could show something that is no longer
     * true. Passing the id also means the sheet survives a rotation on its own - it re-reads, rather
     * than depending on the map to hand the data over again.
     *
     * @param storeId the store whose marker was tapped, resolved from the marker's own store-id
     *                association rather than from its position
     */
    private void openStoreDetailSheet(long storeId) {
        if (!isAdded()) {
            return;
        }
        // Ignore a tap while a details sheet is already showing, rather than stacking two. The same guard
        // the long-press path uses, and for the same reason: two sheets on top of each other is a state
        // the user has no way to reason about.
        if (getParentFragmentManager().findFragmentByTag(StoreDetailBottomSheet.TAG) != null) {
            return;
        }

        StoreDetailBottomSheet.newInstance(storeId)
                .show(getParentFragmentManager(), StoreDetailBottomSheet.TAG);
    }

    private void configureMap() {
        mapView.setTileSource(MapConfig.tileSource());
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);

        // The default camera. It stays on screen only until the first fix arrives, or forever if
        // location is never granted - either way the user sees something meaningful rather than a void.
        mapView.getController().setZoom(MapConfig.DEFAULT_ZOOM);
        mapView.getController().setCenter(new GeoPoint(
                MapConfig.DEFAULT_CENTER_LATITUDE,
                MapConfig.DEFAULT_CENTER_LONGITUDE));
    }

    private void hideLoadingWhenLaidOut(@NonNull View view) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        loadingIndicator.setVisibility(View.GONE);
                    }
                });
    }

    // ------------------------------------------------------------------ observation

    /** Watches the current fix and draws it. */
    private void observeLocation() {
        locationProvider.currentFix().observe(getViewLifecycleOwner(), fix -> {
            if (fix == null) {
                return;
            }
            locationOverlay.render(fix);
            updateStatusChip(fix);

            if (!centredOnFirstFix) {
                centredOnFirstFix = true;
                centreOn(fix);
            }
        });
    }

    /** Watches whether location services are on, so the banner can explain a permanently empty map. */
    private void observeGpsState() {
        locationProvider.locationEnabled().observe(getViewLifecycleOwner(), enabled -> updateBanner());
    }

    /** Watches the network, because a blank map with no connection needs saying out loud. */
    private void observeNetworkState() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return;
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                postBannerUpdate();
            }

            @Override
            public void onLost(@NonNull Network network) {
                postBannerUpdate();
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception e) {
            Log.w(TAG, "Could not observe the network state", e);
        }
    }

    private void postBannerUpdate() {
        View root = getView();
        if (root != null) {
            root.post(this::updateBanner);
        }
    }

    // ------------------------------------------------------------------ the banner

    /**
     * Decides which single problem to report, and shows it.
     *
     * <p>Priority runs from "you cannot use the map at all" to "the map works but is degraded", so the
     * user is never told about a minor issue while a blocking one is on screen.
     */
    private void updateBanner() {
        Activity activity = getActivity();
        if (activity == null || bannerCard == null) {
            return;
        }

        LocationPermissionHelper.State permissionState = LocationPermissionHelper.evaluate(activity);
        boolean locationOn = Boolean.TRUE.equals(locationProvider.locationEnabled().getValue());

        BannerState state;
        if (permissionState == LocationPermissionHelper.State.DENIED_CAN_ASK) {
            state = BannerState.PERMISSION_RATIONALE;
        } else if (permissionState == LocationPermissionHelper.State.DENIED_PERMANENTLY) {
            state = BannerState.PERMISSION_DENIED_PERMANENTLY;
        } else if (!locationOn) {
            state = BannerState.GPS_DISABLED;
        } else if (LocationPermissionHelper.isApproximateOnly(activity)) {
            state = BannerState.PERMISSION_APPROXIMATE;
        } else if (!isOnline()) {
            state = BannerState.OFFLINE;
        } else {
            state = BannerState.NONE;
        }

        applyBanner(state);
    }

    private void applyBanner(BannerState state) {
        if (state == BannerState.NONE) {
            bannerCard.setVisibility(View.GONE);
            return;
        }

        bannerCard.setVisibility(View.VISIBLE);

        switch (state) {
            case PERMISSION_RATIONALE:
                bannerMessage.setText(R.string.map_permission_explanation);
                bannerAction.setText(R.string.map_action_allow_location);
                bannerAction.setOnClickListener(v -> requestLocationPermission());
                break;

            case PERMISSION_DENIED_PERMANENTLY:
                bannerMessage.setText(R.string.map_permission_denied_permanently);
                bannerAction.setText(R.string.map_action_open_settings);
                bannerAction.setOnClickListener(v -> openAppSettings());
                break;

            case GPS_DISABLED:
                bannerMessage.setText(R.string.map_gps_disabled);
                bannerAction.setText(R.string.map_action_open_location_settings);
                bannerAction.setOnClickListener(v -> openLocationSettings());
                break;

            case PERMISSION_APPROXIMATE:
                bannerMessage.setText(R.string.map_permission_approximate);
                bannerAction.setText(R.string.map_action_open_settings);
                bannerAction.setOnClickListener(v -> openAppSettings());
                break;

            case OFFLINE:
            default:
                bannerMessage.setText(R.string.map_offline_notice);
                bannerAction.setText(R.string.map_action_retry);
                bannerAction.setOnClickListener(v -> updateBanner());
                break;
        }
    }

    // ------------------------------------------------------------------ location

    private void requestLocationPermission() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        // Recorded BEFORE the dialog, not after the result: if the user dismisses it by leaving the
        // app, no result is ever delivered and the next launch would think it had never asked.
        LocationPermissionHelper.markRequested(activity);
        permissionLauncher.launch(LocationPermissionHelper.PERMISSIONS);
    }

    /** Re-reads everything location-related. Called after the permission dialog and on resume. */
    private void onLocationStateMaybeChanged() {
        Context context = getContext();
        if (context == null || locationProvider == null) {
            return;
        }

        locationProvider.refreshLocationEnabled();
        syncLocationUpdates();
        updateBanner();

        if (!LocationPermissionHelper.hasLocationPermission(context)) {
            locationOverlay.clear();
            updateStatusChip(null);
        }
    }

    /**
     * Starts or stops location updates to match whether this screen is actually in front of the user.
     *
     * <p>Tabs are hidden rather than destroyed (A27), so {@code onPause} alone is not enough - a hidden
     * Map tab stays resumed. {@link #onHiddenChanged} covers that case, and because the provider guards
     * both calls, asking twice is harmless.
     */
    private void syncLocationUpdates() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        boolean shouldRun = isResumed() && !isHidden()
                && LocationPermissionHelper.hasLocationPermission(context);

        if (shouldRun) {
            locationProvider.startUpdates();
        } else {
            locationProvider.stopUpdates();
        }
    }

    private void centreOnCurrentLocation(boolean animated) {
        LocationFix fix = locationProvider.currentFix().getValue();
        if (fix == null || !fix.hasUsableCoordinates()) {
            // Nothing to centre on yet. Rather than doing nothing silently, say what we are waiting for.
            updateStatusChip(null);
            return;
        }
        centreOn(fix);
    }

    private void centreOn(@NonNull LocationFix fix) {
        if (mapView == null) {
            return;
        }
        mapView.getController().animateTo(new GeoPoint(fix.latitude(), fix.longitude()));
        // Zoom in only when we are still at the wide default - otherwise respect the user's zoom level.
        if (mapView.getZoomLevelDouble() < MapConfig.LOCATION_ZOOM) {
            mapView.getController().setZoom(MapConfig.LOCATION_ZOOM);
        }
    }

    // ------------------------------------------------------------------ status chip

    private void updateStatusChip(@Nullable LocationFix fix) {
        if (statusView == null) {
            return;
        }

        Context context = getContext();
        if (context == null || !LocationPermissionHelper.hasLocationPermission(context)) {
            statusView.setVisibility(View.GONE);
            return;
        }

        if (fix == null) {
            // Permission is granted but no fix has arrived yet - most likely still acquiring satellites.
            statusView.setText(R.string.map_waiting_for_fix);
        } else if (fix.hasAccuracy()) {
            // Saying the accuracy out loud is the point: a 400 m fix should not look like a 4 m one.
            statusView.setText(context.getString(
                    R.string.map_location_accuracy_format, Math.round(fix.accuracyMetres())));
        } else {
            statusView.setVisibility(View.GONE);
            return;
        }

        statusView.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ settings intents

    private void openAppSettings() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not open the app settings page", e);
        }
    }

    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception e) {
            Log.e(TAG, "Could not open the location settings page", e);
        }
    }

    private boolean isOnline() {
        if (connectivityManager == null) {
            return true;
        }
        Network active = connectivityManager.getActiveNetwork();
        if (active == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(active);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        // Coming back from the permission dialog or from the settings pages is the moment the state may
        // have changed, so re-read all of it.
        if (locationProvider != null) {
            onLocationStateMaybeChanged();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        if (locationProvider != null) {
            syncLocationUpdates();
        }
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (locationProvider != null) {
            syncLocationUpdates();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_CENTRED_ON_FIRST_FIX, centredOnFirstFix);
    }

    @Override
    public void onDestroyView() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.w(TAG, "Could not unregister the network callback", e);
            }
            networkCallback = null;
        }

        // Stop updates when the view goes, so a torn-down screen cannot keep the GPS running.
        if (locationProvider != null) {
            locationProvider.stopUpdates();
        }

        if (mapView != null) {
            mapView.onDetach();
            mapView = null;
        }

        locationOverlay = null;

        if (markerController != null) {
            // Tapping must be switched off before the markers go: the listener points back at this
            // fragment, which is about to lose its view.
            markerController.setOnStoreClickListener(null);
            // Detach the markers from the outgoing MapView rather than leaving them in its overlay
            // list, which is about to be discarded along with the view.
            markerController.clear();
            markerController = null;
        }

        loadingIndicator = null;
        bannerCard = null;
        bannerMessage = null;
        bannerAction = null;
        statusView = null;
        myLocationButton = null;
        navBarCard = null;
        navBarTitle = null;

        super.onDestroyView();
    }
}
