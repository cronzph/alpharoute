package com.cph.alpharoute.ui.history;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.MainActivity;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.ui.map.MapConfig;
import com.cph.alpharoute.ui.map.MapMarkerController;
import com.cph.alpharoute.util.DateTimeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.Collections;
import java.util.Locale;

/**
 * Completion Detail - the full record of one completion (Phase 6.4). Screen 11b.
 *
 * <p><b>Only the store id comes in.</b> Everything shown is read back from Room, for the same reason the
 * Store Details sheet does it: a snapshot taken when the previous screen drew its row could already be
 * stale by the time this screen renders (AD-4).
 *
 * <p><b>The marker is green because the store is completed.</b> That is not a styling choice made here -
 * {@code MapMarkerController} picks the icon from the store's own status. If a store reached this screen
 * without being COMPLETED, the marker would say so, which is the honest outcome: the screen plan is
 * explicit that this screen must not silently "fix" an inconsistency, and Phase 6.5's checker is what
 * reports it.
 *
 * <p><b>The map preview is not interactive.</b> It exists to say "here". The action button is how the user
 * goes there, and a second pannable map would just be a worse version of the Map tab.
 */
public class CompletionDetailFragment extends Fragment {

    /** Back-stack tag. */
    public static final String TAG = "CompletionDetailFragment";

    private static final String ARG_HISTORY_ID = "arg_history_id";

    private TextView storeView;
    private TextView nameView;
    private TextView areaView;
    private TextView dateView;
    private TextView timeView;
    private TextView sequenceView;
    private TextView coordinatesView;
    private TextView missingView;
    private MaterialButton viewOnMapButton;
    private MaterialButton undoButton;

    private MapView mapView;
    private MapMarkerController markerController;

    /**
     * The completion row this screen describes.
     *
     * <p><b>A row id, not a store id.</b> Since rounds arrived, a store has one completion per round, so
     * "the store's completion" is not a single thing - and the undo has to name exactly one row.
     */
    private long historyId = -1L;

    /** The store the completion belongs to, read from the row. */
    private long storeId = -1L;

    /** The loaded store, kept so "View on Map" has coordinates without a second read. */
    @Nullable
    private StoreEntity store;

    public CompletionDetailFragment() {
        super(R.layout.fragment_completion_detail);
    }

    /** The record for one completion, identified by its own row id. */
    @NonNull
    public static CompletionDetailFragment newInstance(long historyId) {
        CompletionDetailFragment fragment = new CompletionDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_HISTORY_ID, historyId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            historyId = args.getLong(ARG_HISTORY_ID, -1L);
        }

        storeView = view.findViewById(R.id.tv_detail_store);
        nameView = view.findViewById(R.id.tv_detail_name);
        areaView = view.findViewById(R.id.tv_detail_area);
        dateView = view.findViewById(R.id.tv_detail_date);
        timeView = view.findViewById(R.id.tv_detail_time);
        sequenceView = view.findViewById(R.id.tv_detail_sequence);
        coordinatesView = view.findViewById(R.id.tv_detail_coordinates);
        missingView = view.findViewById(R.id.tv_detail_missing);
        viewOnMapButton = view.findViewById(R.id.btn_detail_view_on_map);
        undoButton = view.findViewById(R.id.btn_detail_undo);

        TextView attribution = view.findViewById(R.id.tv_detail_attribution);
        attribution.setText(MapConfig.attribution());

        mapView = view.findViewById(R.id.detail_map);
        configureMapPreview();

        viewOnMapButton.setOnClickListener(v -> viewOnMap());
        undoButton.setOnClickListener(v -> confirmUndo());

        // The completion first: it names the store, so the store read can follow it rather than being
        // started in parallel and possibly rendering a store with no completion beside it.
        loadCompletion();
    }

    /**
     * Sets up the preview: same tile source and User-Agent as the Map tab, but no gestures.
     *
     * <p>The User-Agent matters even here. The OpenStreetMap tile servers refuse a generic one, and a
     * preview is still a tile request - so a preview built without {@code MapConfig} would come up blank
     * with no error anywhere.
     */
    private void configureMapPreview() {
        if (mapView == null) {
            return;
        }
        mapView.setTileSource(MapConfig.tileSource());
        mapView.setTilesScaledToDpi(true);
        // Not interactive: this is a picture of where the store is, not a place to navigate from.
        mapView.setMultiTouchControls(false);
        mapView.setBuiltInZoomControls(false);
        markerController = new MapMarkerController(mapView);
    }

    // ------------------------------------------------------------------ loading

    /**
     * Reads the completion row, then the store it belongs to.
     *
     * <p>Sequential rather than in parallel: the row is what names the store, so the store read cannot
     * start until it arrives. Doing them concurrently would risk rendering a store beside a completion that
     * had already been undone.
     */
    private void loadCompletion() {
        Context context = getContext();
        if (context == null || historyId <= 0L) {
            showMissingStore();
            return;
        }

        AlphaRouteApp.from(context).completionRepository().findByHistoryId(historyId, history -> {
            if (!isAdded()) {
                return;
            }
            if (history == null) {
                // The row is gone - undone from another screen, or this screen was reopened after the
                // completion was removed. Say so rather than showing a record that no longer exists.
                showMissingStore();
                return;
            }

            storeId = history.storeId;
            renderCompletion(history);
            loadStore();
        });
    }

    private void loadStore() {
        Context context = getContext();
        if (context == null || storeId <= 0L) {
            return;
        }

        AlphaRouteApp.from(context).storeRepository().findById(storeId, loaded -> {
            if (!isAdded() || loaded == null) {
                // The store row is gone. AD-6 makes this impossible while history exists, so if it
                // happens the record is inconsistent - say so rather than showing half a screen.
                showMissingStore();
                return;
            }
            store = loaded;
            render(loaded);
        });
    }

    private void render(@NonNull StoreEntity loaded) {
        storeView.setText(getString(
                R.string.completion_store_format, loaded.routeNumber, loaded.storeCode));
        nameView.setText(loaded.storeName);
        areaView.setText(loaded.area);

        coordinatesView.setText(String.format(
                Locale.US, "%.6f, %.6f", loaded.latitude, loaded.longitude));

        // The marker is chosen from the store's own status by MapMarkerController, so a COMPLETED store
        // gets the green pin with no decision made here.
        if (markerController != null) {
            markerController.renderAll(Collections.singletonList(loaded));
        }

        if (mapView != null) {
            mapView.getController().setZoom(MapConfig.LOCATION_ZOOM);
            mapView.getController().setCenter(new GeoPoint(loaded.latitude, loaded.longitude));
            mapView.invalidate();
        }
    }

    /**
     * The completion half: the date, the time, the round, and the store's position within that day.
     *
     * <p>The round is shown because with rounds a store has one record per pass, and a date and time alone
     * would not say which pass this record belongs to.
     */
    private void renderCompletion(@NonNull CompletionHistoryEntity history) {
        // The date comes from the stored key, not from the timestamp: the key is what the completion
        // transaction filed it under, and formatting the timestamp could disagree after a timezone change.
        dateView.setText(DateTimeHelper.dateTextFromKey(history.completionDateKey));
        timeView.setText(DateTimeHelper.timeText(history.completionTimestamp));
        sequenceView.setText(getString(
                R.string.detail_round_sequence_format,
                history.roundNumber,
                history.dailySequence));
    }

    // ------------------------------------------------------------------ undo

    /**
     * Asks before removing a completion.
     *
     * <p><b>Why a confirmation, and why it names what it removes.</b> The undo deletes a history row - the
     * only destructive operation on the completion table anywhere in the app. It is reachable in two taps
     * from the History list, so it must not be possible to trigger by accident, and the message says exactly
     * what happens: the store goes back to pending and this record goes away. Nothing else is touched, and
     * past rounds are not affected.
     */
    private void confirmUndo() {
        if (!isAdded() || historyId <= 0L) {
            return;
        }

        Context context = getContext();
        if (context == null) {
            return;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.undo_confirm_title)
                .setMessage(R.string.undo_confirm_message)
                .setNegativeButton(R.string.undo_cancel, null)
                .setPositiveButton(R.string.undo_confirm_action, (dialog, which) -> performUndo())
                .show();
    }

    /**
     * Removes the completion.
     *
     * <p>The screen does not close on success. It re-reads the row, finds it gone, and switches to its
     * "record missing" state - which is the honest picture: this completion no longer exists. Closing
     * instead would hide the outcome and leave the user wondering whether it worked.
     */
    private void performUndo() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context).completionRepository().undoCompletion(historyId, outcome -> {
            if (!isAdded()) {
                return;
            }

            switch (outcome.status()) {
                case SUCCESS:
                case COMPLETION_NOT_FOUND:
                    // The requested end state - no such record - is the current state either way, so both
                    // are reported the same. "Already gone" is not a failure.
                    showMissingStore();
                    toast(getString(outcome.isSuccess()
                            ? R.string.undo_result_done
                            : R.string.undo_result_already_gone));
                    break;

                case STORE_NOT_FOUND:
                    showMissingStore();
                    toast(getString(R.string.undo_result_store_missing));
                    break;

                case FAILED:
                default:
                    // Rolled back - the row is still there and the store is untouched. Nothing was lost,
                    // so the screen stays as it is and the user can try again.
                    toast(getString(R.string.undo_result_failed));
                    break;
            }
        });
    }

    private void toast(@NonNull String message) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    private void showMissingStore() {
        if (!isAdded()) {
            return;
        }
        missingView.setVisibility(View.VISIBLE);
        viewOnMapButton.setVisibility(View.GONE);
        // The undo goes too. There is no completion left to remove, and a button that could only answer
        // "already gone" is worse than no button.
        undoButton.setVisibility(View.GONE);
        storeView.setText(R.string.detail_store_missing_title);
        nameView.setVisibility(View.GONE);
        areaView.setVisibility(View.GONE);
        if (mapView != null) {
            mapView.setVisibility(View.GONE);
        }
    }

    /**
     * Jumps to the Map tab, centred on this store.
     *
     * <p>The coordinate goes through the same channel the pasted-link flow uses, so the Map tab needs no
     * new wiring - it already observes that and moves its camera. The tab switch happens first, so the map
     * is on screen by the time the camera moves rather than moving behind a hidden tab.
     */
    private void viewOnMap() {
        if (!isAdded() || store == null) {
            return;
        }
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showMapAt(
                    new GeoCoordinate(store.latitude, store.longitude));
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        // onDetach is mandatory: without it the map view leaks, and because a pushed screen is recreated
        // on every visit the leak would accumulate. See the Map tab's own note.
        if (mapView != null) {
            if (markerController != null) {
                markerController.clear();
                markerController = null;
            }
            mapView.onDetach();
            mapView = null;
        }

        storeView = null;
        nameView = null;
        areaView = null;
        dateView = null;
        timeView = null;
        sequenceView = null;
        coordinatesView = null;
        missingView = null;
        viewOnMapButton = null;
        undoButton = null;

        super.onDestroyView();
    }
}
