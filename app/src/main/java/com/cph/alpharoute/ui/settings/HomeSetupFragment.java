package com.cph.alpharoute.ui.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.repository.WriteOutcome;
import com.cph.alpharoute.location.LocationFix;
import com.cph.alpharoute.location.LocationPermissionHelper;
import com.cph.alpharoute.location.LocationProvider;
import com.cph.alpharoute.ui.map.MapConfig;
import com.cph.alpharoute.ui.map.MapMarkerController;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;

/**
 * Home Setup - screen 6 of the Phase 0.3 screen plan. Chooses where the route finishes.
 *
 * <p>A <b>pushed fragment</b>, opened from Settings and pushed onto the back stack above the tabs. That
 * is why it has its own Back action and why {@code MainActivity} hides the bottom navigation while it is
 * open: it is not a destination, it is a step, and the way out is back to where you came from.
 *
 * <p><b>Home is not a store.</b> It is never written to the {@code stores} table, it never appears in a
 * count, and it is drawn with its own house pin. Setting Home does not touch completion history in any
 * way. That is the whole reason it lives in the settings row instead.
 *
 * <p><b>Choosing a point.</b> A tap on the map places or moves the Home pin. That is the only gesture -
 * the map is always ready for it - and {@code Use current location} is the shortcut for when the user is
 * already standing where Home should be.
 *
 * <p><b>The candidate is held in a field and saved in the instance state.</b> It is a value the user is
 * producing right now, not something handed in, so arguments would be the wrong place for it - unlike the
 * store id on the Store Details sheet, which is handed in and therefore belongs in the arguments.
 *
 * <p><b>Nothing is written until Confirm.</b> Panning, zooming and tapping the map change nothing but
 * this screen's own state, so a user who wanders off with the Back action has changed nothing at all.
 */
public class HomeSetupFragment extends Fragment {

    private static final String STATE_HAS_CANDIDATE = "state_has_candidate";
    private static final String STATE_CANDIDATE_LATITUDE = "state_candidate_latitude";
    private static final String STATE_CANDIDATE_LONGITUDE = "state_candidate_longitude";
    private static final String STATE_CENTRED_ON_FIRST_FIX = "state_centred_on_first_fix";

    private MapView mapView;
    private LinearProgressIndicator loadingIndicator;
    private TextView coordinatesView;
    private MaterialButton confirmButton;
    private MaterialButton useLocationButton;

    private MapMarkerController markerController;
    private LocationProvider locationProvider;

    /** The point the user is considering. Both null until one has been chosen. */
    @Nullable
    private Double candidateLatitude;
    @Nullable
    private Double candidateLongitude;

    /** True while the save is in flight, so a second tap cannot send a second write. */
    private boolean saving;

    /** True once the camera has been moved to the first fix, so it is never moved out from under the user. */
    private boolean centredOnFirstFix;

    /**
     * Registered as a field, because {@code registerForActivityResult} must happen before the fragment is
     * created. The registry is only resolved when {@code launch()} is called, so this is safe even though
     * the fragment is not attached yet.
     */
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    granted -> onLocationStateMaybeChanged());

    public HomeSetupFragment() {
        super(R.layout.fragment_home_setup);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = view.findViewById(R.id.map_home_setup);
        loadingIndicator = view.findViewById(R.id.home_setup_loading);
        coordinatesView = view.findViewById(R.id.tv_home_setup_coordinates);
        confirmButton = view.findViewById(R.id.btn_home_setup_confirm);
        useLocationButton = view.findViewById(R.id.btn_home_setup_use_location);

        TextView attribution = view.findViewById(R.id.tv_home_setup_attribution);
        attribution.setText(MapConfig.attribution());

        locationProvider = AlphaRouteApp.from(requireContext()).locationProvider();

        configureMap();
        markerController = new MapMarkerController(mapView);
        addTapHandler();

        restoreCandidate(savedInstanceState);

        MaterialButton backButton = view.findViewById(R.id.btn_home_setup_back);
        backButton.setOnClickListener(v -> leave());
        confirmButton.setOnClickListener(v -> onConfirmClicked());
        useLocationButton.setOnClickListener(v -> onUseLocationClicked());

        if (hasCandidate()) {
            drawCandidate();
        } else {
            // Start from the Home that is already saved, when there is one, so "change home" opens on
            // where Home currently is rather than on nothing at all.
            loadExistingHome();
        }

        observeLocation();
        updateActions();
    }

    // ------------------------------------------------------------------ map

    private void configureMap() {
        mapView.setTileSource(MapConfig.tileSource());
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);

        // The wide default, as on the Map tab. It is only on screen until the camera moves to something
        // meaningful - the saved Home, or the first fix.
        mapView.getController().setZoom(MapConfig.DEFAULT_ZOOM);
        mapView.getController().setCenter(new GeoPoint(
                MapConfig.DEFAULT_CENTER_LATITUDE,
                MapConfig.DEFAULT_CENTER_LONGITUDE));
    }

    /**
     * Turns a tap on the map into the chosen point.
     *
     * <p><b>The overlay goes in at index 0, and the Home pin sits above it returning false for taps.</b>
     * osmdroid walks the overlays from the top down and stops at the first one that returns true
     * ({@code DefaultOverlayManager.onSingleTapConfirmed}). Putting this at the bottom means the pin is
     * drawn above it, while still letting a tap on the pin itself fall through to here - so tapping Home
     * moves Home, which is what you want when you are nudging it into position.
     */
    private void addTapHandler() {
        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                if (point == null) {
                    return false;
                }
                // No centring: the point is already under the user's finger.
                setCandidate(point.getLatitude(), point.getLongitude(), false);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                // A tap is the gesture on this screen; a long press is not used for anything. Returning
                // false leaves it alone rather than pretending to handle it.
                return false;
            }
        });

        mapView.getOverlays().add(0, eventsOverlay);
    }

    /** Moves the camera to a point, zooming in only if we are still at the wide default. */
    private void centreOn(double latitude, double longitude) {
        if (mapView == null) {
            return;
        }
        mapView.getController().animateTo(new GeoPoint(latitude, longitude));
        if (mapView.getZoomLevelDouble() < MapConfig.LOCATION_ZOOM) {
            mapView.getController().setZoom(MapConfig.LOCATION_ZOOM);
        }
    }

    // ------------------------------------------------------------------ the candidate

    private void restoreCandidate(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        centredOnFirstFix = savedInstanceState.getBoolean(STATE_CENTRED_ON_FIRST_FIX, false);

        if (savedInstanceState.getBoolean(STATE_HAS_CANDIDATE, false)) {
            candidateLatitude = savedInstanceState.getDouble(STATE_CANDIDATE_LATITUDE);
            candidateLongitude = savedInstanceState.getDouble(STATE_CANDIDATE_LONGITUDE);
        }
    }

    /**
     * Opens on the Home that is already saved, so "change home location" starts from where Home is.
     *
     * <p>Only when the user has not already chosen something - a tap can arrive before this read
     * returns, and it must not be overwritten by the older value.
     */
    private void loadExistingHome() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        AlphaRouteApp.from(activity).settingsRepository().getNow(settings -> {
            if (!isAdded() || settings == null || !settings.isHomeSet() || hasCandidate()) {
                return;
            }
            setCandidate(settings.homeLatitude, settings.homeLongitude, true);
        });
    }

    private void setCandidate(double latitude, double longitude, boolean centre) {
        candidateLatitude = latitude;
        candidateLongitude = longitude;
        drawCandidate();
        if (centre) {
            centreOn(latitude, longitude);
        }
        updateActions();
    }

    private void drawCandidate() {
        if (markerController != null) {
            // The controller refuses anything that is not a real coordinate, so a bad value cannot become
            // a pin. It is also what draws the saved Home on the Map tab - the same pin, which is exactly
            // right: this IS the Home pin, it simply has not been saved yet.
            markerController.renderHome(candidateLatitude, candidateLongitude);
        }
    }

    private boolean hasCandidate() {
        return candidateLatitude != null && candidateLongitude != null;
    }

    // ------------------------------------------------------------------ location

    private void observeLocation() {
        locationProvider.currentFix().observe(getViewLifecycleOwner(), fix -> {
            if (fix != null && !hasCandidate() && !centredOnFirstFix) {
                // Centred once, so the user is not left staring at the wrong part of the country. After
                // that the camera is theirs, and it is never moved out from under a tap.
                centredOnFirstFix = true;
                centreOn(fix.latitude(), fix.longitude());
            }
            updateActions();
        });
    }

    /** Re-reads everything location-related. Called after the permission dialog and on resume. */
    private void onLocationStateMaybeChanged() {
        if (locationProvider == null) {
            return;
        }
        locationProvider.refreshLocationEnabled();
        syncLocationUpdates();
        updateActions();
    }

    /**
     * Starts or stops location updates to match whether this screen is actually in front of the user.
     *
     * <p>Unlike the Map tab, this screen is pushed rather than a tab, so it is never merely hidden and
     * {@code onPause} is a reliable signal. {@code onHiddenChanged} is therefore not needed here.
     */
    private void syncLocationUpdates() {
        if (locationProvider == null) {
            return;
        }

        boolean shouldRun = isResumed()
                && LocationPermissionHelper.hasLocationPermission(requireContext());

        if (shouldRun) {
            locationProvider.startUpdates();
        } else {
            locationProvider.stopUpdates();
        }
    }

    private void onUseLocationClicked() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        LocationPermissionHelper.State state = LocationPermissionHelper.evaluate(activity);

        if (state == LocationPermissionHelper.State.GRANTED) {
            LocationFix fix = currentFix();
            if (fix == null || !fix.hasUsableCoordinates()) {
                // The button is disabled in this state, so arriving here means a race. Say what happened
                // rather than doing nothing at all.
                Snackbar.make(requireView(), R.string.home_setup_no_fix, Snackbar.LENGTH_LONG).show();
                return;
            }
            setCandidate(fix.latitude(), fix.longitude(), true);
            return;
        }

        if (state == LocationPermissionHelper.State.DENIED_PERMANENTLY) {
            // The system will not show the dialog again, so asking is pointless. The Map tab carries the
            // full explanation and a button that opens the app settings page; here the message says what
            // to do, and the map can still be tapped instead.
            Snackbar.make(requireView(), R.string.map_permission_denied_permanently,
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        // Recorded BEFORE the dialog, not after the result: if the user dismisses it by leaving the app,
        // no result is ever delivered and the next launch would think it had never asked.
        LocationPermissionHelper.markRequested(activity);
        permissionLauncher.launch(LocationPermissionHelper.PERMISSIONS);
    }

    @Nullable
    private LocationFix currentFix() {
        return locationProvider == null ? null : locationProvider.currentFix().getValue();
    }

    /**
     * True while a fix has been asked for but has not arrived.
     *
     * <p>Only when permission has actually been granted - without it the app is not waiting for anything,
     * it is blocked, and "Locating…" would be a lie.
     */
    private boolean isWaitingForFix() {
        Activity activity = getActivity();
        if (activity == null || !LocationPermissionHelper.hasLocationPermission(activity)) {
            return false;
        }
        return currentFix() == null;
    }

    // ------------------------------------------------------------------ saving

    private void onConfirmClicked() {
        if (saving || !hasCandidate()) {
            return;
        }

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        saving = true;
        confirmButton.setEnabled(false);

        // setHome() validates the coordinate range itself and refuses anything outside the globe, so a
        // failure here is reported rather than stored.
        AlphaRouteApp.from(activity).settingsRepository()
                .setHome(candidateLatitude, candidateLongitude, this::onHomeSaved);
    }

    private void onHomeSaved(@NonNull WriteOutcome outcome) {
        if (!isAdded()) {
            // The screen went away while the write was in flight. Home is saved; there is nobody to tell.
            return;
        }

        saving = false;

        if (outcome == WriteOutcome.SUCCESS) {
            Toast.makeText(requireContext(), R.string.home_setup_saved, Toast.LENGTH_SHORT).show();
            // Back to Settings, which observes the settings row and will already be showing the new Home.
            // The map will draw the new pin from the same observation.
            leave();
            return;
        }

        confirmButton.setEnabled(true);
        Snackbar.make(requireView(), R.string.home_setup_save_failed, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Pops this screen off the back stack.
     *
     * <p>{@code MainActivity} watches the back stack and restores the toolbar title and the bottom
     * navigation when this happens, so the chrome comes back without this screen having to know about it.
     */
    private void leave() {
        getParentFragmentManager().popBackStack();
    }

    // ------------------------------------------------------------------ view state

    /** Brings every control in line with the current state. One place, so they cannot disagree. */
    private void updateActions() {
        if (coordinatesView == null) {
            return;
        }

        updateReadout();
        updateUseLocationButton();

        confirmButton.setEnabled(hasCandidate() && !saving);
        loadingIndicator.setVisibility(isWaitingForFix() ? View.VISIBLE : View.GONE);
    }

    private void updateReadout() {
        if (hasCandidate()) {
            coordinatesView.setText(
                    getString(R.string.coordinates_format, candidateLatitude, candidateLongitude));
            return;
        }
        if (isWaitingForFix()) {
            coordinatesView.setText(R.string.home_setup_locating);
            return;
        }
        coordinatesView.setText(R.string.home_setup_not_chosen);
    }

    /**
     * Relabels and enables the location button to match the permission state.
     *
     * <p>Three states, three behaviours: with permission it offers the current position and is disabled
     * until there is a usable fix; without it, it offers to ask; and when permission has been refused for
     * good, asking would do nothing, so the tap explains where to change it instead.
     */
    private void updateUseLocationButton() {
        Activity activity = getActivity();
        if (activity == null || useLocationButton == null) {
            return;
        }

        if (LocationPermissionHelper.evaluate(activity)
                != LocationPermissionHelper.State.GRANTED) {
            useLocationButton.setText(R.string.home_setup_action_grant_permission);
            useLocationButton.setEnabled(true);
            return;
        }

        useLocationButton.setText(R.string.home_setup_action_use_location);
        LocationFix fix = currentFix();
        useLocationButton.setEnabled(fix != null && fix.hasUsableCoordinates());
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        // Coming back from the permission dialog is the moment the state may have changed.
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(STATE_CENTRED_ON_FIRST_FIX, centredOnFirstFix);

        // Only written when there is one: a "has candidate" flag plus two doubles, so that a candidate at
        // latitude 0 is restored as a candidate rather than mistaken for "nothing chosen".
        if (hasCandidate()) {
            outState.putBoolean(STATE_HAS_CANDIDATE, true);
            outState.putDouble(STATE_CANDIDATE_LATITUDE, candidateLatitude);
            outState.putDouble(STATE_CANDIDATE_LONGITUDE, candidateLongitude);
        }
    }

    @Override
    public void onDestroyView() {
        // Stop updates when the view goes, so a torn-down screen cannot keep the GPS running.
        if (locationProvider != null) {
            locationProvider.stopUpdates();
        }

        if (mapView != null) {
            mapView.onDetach();
            mapView = null;
        }

        if (markerController != null) {
            // Detach the pins from the outgoing MapView rather than leaving them in its overlay list,
            // which is about to be discarded along with the view.
            markerController.clear();
            markerController = null;
        }

        loadingIndicator = null;
        coordinatesView = null;
        confirmButton = null;
        useLocationButton = null;

        super.onDestroyView();
    }
}
