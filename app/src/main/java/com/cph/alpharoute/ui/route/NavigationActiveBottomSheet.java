package com.cph.alpharoute.ui.route;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.ArrivalState;
import com.cph.alpharoute.location.LocationPermissionHelper;
import com.cph.alpharoute.navigation.NavigationLauncher;
import com.cph.alpharoute.navigation.NavigationSession;
import com.cph.alpharoute.navigation.NavigationSessionManager;
import com.cph.alpharoute.ui.completion.CompletionConfirmDialogFragment;
import com.cph.alpharoute.util.RouteTextFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Navigation Active - screen 8 of the Phase 0.3 screen plan.
 *
 * <p><b>This sheet does not guide the drive.</b> Under D-6/D-7 the Google Maps app does that, along with
 * the live traffic and the road rules. What this screen does is the one thing Google Maps cannot: it
 * watches this app's own arrival radius for this app's own store, and it is where the driver says they
 * have finished with one.
 *
 * <p><b>The arrival banner reports; it does not gate.</b> It says arrived, approaching, outside, or no fix
 * - and the completion button is offered whatever it says. The gate that decides whether a store is
 * counted is the confirmation dialog's YES, not a radius: see {@link #renderCompletion} for why the radius
 * stopped being the second gate.
 *
 * <p><b>It reads the session and Room, and it holds neither.</b> The destination is a store <i>id</i> in
 * {@link NavigationSession}; the code and name are read back from Room so a store renamed while the
 * driver is on their way cannot leave a stale name on screen (AD-4). The distance and the arrival state
 * come from the session, which the foreground service fills in - so this sheet and the service never need
 * to know that the other exists.
 *
 * <p><b>It survives the sheet being closed.</b> Closing this sheet does not end the navigation: the
 * session keeps running and the compact bar on the map is the way back in. Ending the drive is a decision,
 * and only the <b>End navigation</b> button makes it.
 */
public class NavigationActiveBottomSheet extends BottomSheetDialogFragment {

    /** Dialog tag, so the map can check whether it is already showing rather than stacking two. */
    public static final String TAG = "NavigationActiveBottomSheet";

    private TextView storeView;
    private View distanceGroup;
    private TextView distanceView;
    private TextView messageView;
    private TextView arrivalView;
    private MaterialButton recoverButton;
    private MaterialButton confirmButton;

    private NavigationSession session;
    private NavigationSessionManager sessionManager;

    /** The active destination, cached so the store can be read once rather than on every fix. */
    private long storeId = NavigationSession.NO_STORE;

    /**
     * The latest distance, or {@code NaN}.
     *
     * <p>A field rather than a local, because the recovery button's visibility depends on it and the
     * button can be re-rendered by something other than a session emission - a return from the permission
     * dialog, which is not a session change at all.
     */
    private double currentDistance = Double.NaN;

    public NavigationActiveBottomSheet() {
        super(R.layout.sheet_navigation_active);
    }

    /** Creates the sheet. It takes no arguments - the session is the argument. */
    public static NavigationActiveBottomSheet newInstance() {
        return new NavigationActiveBottomSheet();
    }

    /**
     * Registered as a field, because {@code registerForActivityResult} must happen before the fragment is
     * created - the same reason the Map tab holds its launcher this way.
     */
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    granted -> {
                        // The map is deliberately ignored. What the system now reports is the authority,
                        // and re-rendering re-reads it - which also covers a partial grant.
                        renderRecovery();
                    });

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        storeView = view.findViewById(R.id.tv_nav_store);
        distanceGroup = view.findViewById(R.id.group_nav_distance);
        distanceView = view.findViewById(R.id.tv_nav_distance);
        messageView = view.findViewById(R.id.tv_nav_message);
        arrivalView = view.findViewById(R.id.tv_nav_arrival);
        recoverButton = view.findViewById(R.id.btn_nav_recover);
        confirmButton = view.findViewById(R.id.btn_nav_confirm);

        Context context = requireContext();
        AlphaRouteApp app = AlphaRouteApp.from(context);
        session = app.navigationSession();
        sessionManager = app.navigationSessionManager();

        MaterialButton endButton = view.findViewById(R.id.btn_nav_end);
        MaterialButton openMapsButton = view.findViewById(R.id.btn_nav_open_maps);

        endButton.setOnClickListener(v -> endNavigation());
        openMapsButton.setOnClickListener(v -> reopenGoogleMaps());

        // The session is observed for the whole life of the view, so the distance and the arrival
        // indicator follow the driver without anything polling.
        session.observe().observe(getViewLifecycleOwner(), this::render);
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Brings the whole sheet in line with one session snapshot.
     *
     * <p>A {@code null} snapshot means the session ended - the user tapped End navigation in another
     * window, or the drive was completed. This sheet has nothing left to describe, so it closes rather
     * than sitting there showing the last known distance for a drive that is over.
     */
    private void render(@Nullable NavigationSession.Snapshot snapshot) {
        if (snapshot == null) {
            if (isAdded()) {
                dismissAllowingStateLoss();
            }
            return;
        }

        if (snapshot.storeId() != storeId) {
            storeId = snapshot.storeId();
            loadStoreLabel();
        }

        ArrivalState state = snapshot.arrivalState();
        double distance = snapshot.distanceMetres();
        boolean hasDistance = !Double.isNaN(distance);
        currentDistance = distance;

        distanceGroup.setVisibility(hasDistance ? View.VISIBLE : View.GONE);
        messageView.setVisibility(hasDistance ? View.GONE : View.VISIBLE);

        if (hasDistance) {
            distanceView.setText(RouteTextFormatter.distanceText((int) Math.round(distance)));
        } else {
            // Two different reasons for having no distance, and they need different words. "Waiting for
            // your location" is a few seconds of patience; "Location unavailable" means permission was
            // lost or the provider is off, which the user has to act on.
            messageView.setText(state == ArrivalState.NO_DESTINATION
                    ? R.string.nav_sheet_standby
                    : R.string.nav_sheet_no_fix);
        }

        renderArrival(state);
        renderRecovery();
        renderCompletion(state);
    }

    /**
     * Shows the completion action, for as long as there is a store being driven to.
     *
     * <p><b>This is no longer gated on {@link ArrivalState#ARRIVED}.</b> It used to be, on the reasoning
     * that a pending store outside the radius should not offer a completion at all. In the field that
     * failed the same way it had already failed on the Store Details sheet: the button disappeared -
     * sometimes for good - for exactly the drivers who needed it most. A covered market with no fix, a
     * signal that drops, a radius that GPS jitter alone can explain, or simply having driven on before
     * tapping. <b>"No delay, no signal, no button" was worse than the false completion the gate was built
     * to prevent.</b>
     *
     * <p><b>The gate that matters is untouched.</b> The arrival banner above still says where the driver
     * is - arrived, approaching, outside, or no fix at all - so the app never claims an arrival it cannot
     * see. And this button still opens the confirmation dialog rather than completing anything: that
     * dialog, and the YES on it, is the one thing nothing in the app is counted without.
     */
    private void renderCompletion(@NonNull ArrivalState state) {
        if (confirmButton == null) {
            return;
        }

        // Asked of the destination, not of the arrival state. If there is a store being driven to, the
        // question "did you do this one?" is a fair one to ask wherever the driver happens to be.
        boolean hasDestination = storeId != NavigationSession.NO_STORE;
        confirmButton.setVisibility(hasDestination ? View.VISIBLE : View.GONE);

        if (hasDestination) {
            // Set on every render rather than once, so the button cannot be left pointing at a store
            // that has since changed underneath the sheet.
            confirmButton.setOnClickListener(v -> openCompletionDialog());
        } else {
            confirmButton.setOnClickListener(null);
        }
    }

    /**
     * Opens the completion confirmation dialog.
     *
     * <p>The distance travels with it so the dialog can show the evidence for the question. It may be
     * {@code NaN} if the session has no distance for any reason - the dialog handles that by omitting the
     * line rather than inventing a number.
     */
    private void openCompletionDialog() {
        if (!isAdded() || storeId == NavigationSession.NO_STORE) {
            return;
        }
        // The same guard the other sheets use: two copies of a confirmation dialog is a state the user
        // cannot reason about, and here it would mean two chances to tap YES.
        if (getParentFragmentManager().findFragmentByTag(CompletionConfirmDialogFragment.TAG) != null) {
            return;
        }

        CompletionConfirmDialogFragment.newInstance(storeId, currentDistance)
                .show(getParentFragmentManager(), CompletionConfirmDialogFragment.TAG);
    }

    /**
     * Shows the one recovery action the current failure needs, or nothing.
     *
     * <p><b>A missing position always means something has broken</b> - a granted permission with a working
     * provider produces a fix within seconds - so "no distance" is the trigger. Which of the two actions
     * to offer is then decided by permission state, not by the distance:
     *
     * <ul>
     *   <li><b>Permission gone</b> → "Grant permission". Without it nothing else can work, so offering
     *       "Resume monitoring" would be a button that fails.</li>
     *   <li><b>Permission fine, but the service is not running</b> → "Resume monitoring". This is the
     *       system-reclaimed-the-service case, and a tap genuinely fixes it.</li>
     * </ul>
     */
    private void renderRecovery() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        boolean hasDistance = !Double.isNaN(currentDistance);
        if (hasDistance) {
            recoverButton.setVisibility(View.GONE);
            return;
        }

        if (!LocationPermissionHelper.hasLocationPermission(context)) {
            recoverButton.setText(R.string.nav_location_lost_action);
            recoverButton.setOnClickListener(v -> requestLocationPermission());
            recoverButton.setVisibility(View.VISIBLE);
            return;
        }

        if (!session.isMonitoring()) {
            recoverButton.setText(R.string.nav_monitoring_stopped_action);
            recoverButton.setOnClickListener(v -> resumeMonitoring());
            recoverButton.setVisibility(View.VISIBLE);
            return;
        }

        // Permission is fine and the service is running, so a missing fix is just the few seconds before
        // the first reading arrives. Nothing to offer.
        recoverButton.setVisibility(View.GONE);
    }

    /**
     * Paints the arrival indicator.
     *
     * <p>The text carries the meaning and the colour only reinforces it - the same rule the store status
     * chip follows, and for the same reason: the app's green and orange are the classic red/green
     * confusion pair, and a driver glancing at a colour has to be able to fall back on the words.
     */
    private void renderArrival(@NonNull ArrivalState state) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        arrivalView.setText(messageFor(state));
        arrivalView.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, containerFor(state))));
        arrivalView.setTextColor(ContextCompat.getColor(context, textColorFor(state)));
    }

    private static int messageFor(@NonNull ArrivalState state) {
        switch (state) {
            case ARRIVED:
                return R.string.nav_sheet_arrived;
            case APPROACHING:
                return R.string.nav_sheet_approaching;
            case NO_FIX:
                return R.string.nav_sheet_no_fix;
            case OUTSIDE:
            case NO_DESTINATION:
            default:
                return R.string.nav_sheet_outside;
        }
    }

    @ColorRes
    private static int containerFor(@NonNull ArrivalState state) {
        // Arrived uses the completed container, because "you are here" and "this store is done" are the
        // same colour in this app's language. Everything else is the pending colour, which reads as
        // "still in progress" without implying a problem.
        return state == ArrivalState.ARRIVED
                ? R.color.status_completed_container
                : R.color.status_pending_container;
    }

    @ColorRes
    private static int textColorFor(@NonNull ArrivalState state) {
        return state == ArrivalState.ARRIVED
                ? R.color.status_on_completed_container
                : R.color.status_on_pending_container;
    }

    // ------------------------------------------------------------------ the store

    /**
     * Reads the store's code and name from Room.
     *
     * <p>Once per destination, not once per fix. A fix arrives every few seconds and the store cannot
     * change behind the sheet without the sheet being rebuilt - and if it were deleted, the session's
     * destination would still be a valid coordinate, so the drive is not interrupted by a failed lookup.
     */
    private void loadStoreLabel() {
        Context context = getContext();
        if (context == null || storeId == NavigationSession.NO_STORE) {
            return;
        }

        AlphaRouteApp.from(context).storeRepository().findById(storeId, store -> {
            if (!isAdded() || store == null) {
                return;
            }
            storeView.setText(getString(R.string.nav_sheet_store_format, labelFor(store)));
        });
    }

    private static String labelFor(@NonNull StoreEntity store) {
        return store.routeNumber + " — " + store.storeName;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Re-opens the navigation app at the destination.
     *
     * <p>The driver may have closed Google Maps, or the sheet may have been opened from the notification
     * after the fact. Either way the destination is still in the session, and the hand-off is the same
     * one Phase 4.2 built.
     */
    private void reopenGoogleMaps() {
        NavigationSession.Snapshot snapshot = session.current();
        if (snapshot == null) {
            return;
        }

        boolean launched = NavigationLauncher.launch(
                requireContext(),
                snapshot.destination().latitude(),
                snapshot.destination().longitude());

        if (!launched) {
            Snackbar.make(requireView(), R.string.nav_no_navigation_app, Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Ends the session: stops monitoring, clears the persisted reference, dismisses this sheet.
     *
     * <p>Ending is a decision the user makes, and nothing ends a session on their behalf - not closing
     * this sheet, not leaving the map, not the app being backgrounded. A drive that stopped being
     * monitored because the user glanced at another tab would miss the arrival, which is the whole point.
     */
    private void endNavigation() {
        sessionManager.stop(requireContext());
        dismissAllowingStateLoss();
    }

    /**
     * Re-asks for location permission, then re-renders.
     *
     * <p>The result is re-evaluated by re-rendering rather than by trusting the callback's map. The system
     * is the authority on what was actually granted - including a partial "approximate only" answer - and
     * {@link LocationPermissionHelper} is the single place that reads it.
     */
    private void requestLocationPermission() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        // Recorded BEFORE the dialog, for the same reason the Map tab does it: a dialog dismissed by
        // leaving the app delivers no result, and the next launch would think it had never asked.
        LocationPermissionHelper.markRequested(activity);
        permissionLauncher.launch(LocationPermissionHelper.PERMISSIONS);
    }

    /**
     * Restarts the monitoring service for the session that is already active.
     *
     * <p>Legal from here because the user is looking at this sheet and has just tapped a button - which is
     * exactly the condition Android 12+ requires for a foreground service start.
     */
    private void resumeMonitoring() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        boolean started = sessionManager.restartMonitoring(context);
        if (!started) {
            Snackbar.make(requireView(), R.string.nav_monitoring_stopped, Snackbar.LENGTH_LONG).show();
        }
        renderRecovery();
    }
}
