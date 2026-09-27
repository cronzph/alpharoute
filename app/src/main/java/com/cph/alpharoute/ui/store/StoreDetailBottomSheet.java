package com.cph.alpharoute.ui.store;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.data.repository.WriteOutcome;
import com.cph.alpharoute.domain.ArrivalEvaluator;
import com.cph.alpharoute.domain.ArrivalState;
import com.cph.alpharoute.domain.DistanceCalculator;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.location.LocationFix;
import com.cph.alpharoute.ui.completion.CompletionConfirmDialogFragment;
import com.cph.alpharoute.ui.route.RoutePreviewBottomSheet;
import com.cph.alpharoute.util.DateTimeHelper;
import com.cph.alpharoute.util.GeoConstants;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

/**
 * The Store Details sheet - what one store is, opened by tapping its marker on the map.
 *
 * <p>Screen 5 of the Phase 0.3 screen plan. Phase 3.5 built it as a <b>read-only</b> view. Phase 3.6
 * gives it two actions: <b>Edit</b>, which opens the Add / Edit form for this store, and <b>Delete</b>,
 * which asks for confirmation first and is unavailable for a store that has been completed.
 *
 * <p><b>It is opened with a store id, and nothing else.</b> Not the store object, and not a copy of its
 * fields. The sheet re-reads the store from Room with {@link
 * com.cph.alpharoute.data.repository.StoreRepository#findById} as soon as it is shown. That matters for
 * two reasons:
 *
 * <ul>
 *   <li><b>Room stays the single source of truth (AD-4).</b> A store passed in from the map would be a
 *       snapshot taken when the marker was drawn, and the sheet could then display something that is no
 *       longer true. Re-reading means the sheet can only ever show what is actually stored.</li>
 *   <li><b>It survives rotation for free.</b> The id travels in the fragment's arguments, so when
 *       FragmentManager recreates the sheet through its no-argument constructor the lookup simply runs
 *       again. Holding the store in a field would have lost it - the same trap that would pin a new
 *       store at latitude 0 (assumption A62 in the Phase 3.3 record).</li>
 * </ul>
 *
 * <p><b>It carries a completion action, and it is available from anywhere - that is a decision, not an
 * oversight.</b> This sheet used to say the opposite: that a pending store could not be marked complete
 * from here or anywhere else, and that completion was reachable only through the arrival flow. In the
 * field that rule failed the drivers who needed it most - no fix under a roof, a signal that drops, or a
 * radius that GPS jitter alone can explain - and it produced "no delay, no signal, no button". So the
 * completion button is now offered for every pending store, wherever it is.
 *
 * <p><b>What actually guards a completion is unchanged, and it was never this sheet.</b> The button opens
 * {@code CompletionConfirmDialogFragment}, which asks "Store completed?" and requires a YES. That dialog
 * is the one thing in the app nothing is counted without - it is the gate, and it re-reads the store and
 * re-checks its status before it writes. This sheet only decides whether the question can be asked.
 *
 * <p><b>What is not on the sheet yet.</b> The Phase 0.3 plan gives screen 5 three actions. Edit and
 * Delete are here as of Phase 3.6; <b>Get Directions</b> is the remaining one, and it arrives with the
 * route preview in Phase 4.2. It is not drawn before then because a button that does nothing when tapped
 * is worse than a button that is not there.
 */
public class StoreDetailBottomSheet extends BottomSheetDialogFragment {

    /**
     * Dialog tag, so the map can check whether the sheet is already showing rather than stacking two.
     *
     * <p>Unlike the Add / Edit sheet, this one publishes no fragment result. It changes nothing, so
     * there is nothing for the map to be told about.
     */
    public static final String TAG = "StoreDetailBottomSheet";

    private static final String ARG_STORE_ID = "arg_store_id";

    /**
     * A store id that cannot exist.
     *
     * <p>Room's {@code autoGenerate} primary key starts at 1, so -1 is never a real store. It is the
     * value the sheet holds when it was opened without a usable id, which is treated exactly like a
     * store that has since been deleted.
     */
    private static final long NO_STORE_ID = -1L;

    private long storeId = NO_STORE_ID;

    // ------------------------------------------------------------------ views

    private LinearProgressIndicator loading;
    private View contentGroup;
    private TextView statusChip;
    private TextView routeNumberView;
    private TextView storeCodeView;
    private TextView storeNameView;
    private TextView areaView;
    private TextView coordinatesView;
    private View completionGroup;
    private TextView completedDateView;
    private TextView completedTimeView;
    private TextView dailyOrderView;
    private MaterialButton editButton;
    private MaterialButton deleteButton;
    private TextView deleteNote;
    private MaterialButton directionsButton;
    private MaterialButton completeButton;
    private TextView atLocationNote;

    /**
     * The store being shown, cached so the arrival check has its coordinates without a second read.
     *
     * <p>Set once when the store loads. A store cannot move while this sheet is open without the sheet
     * being rebuilt, so a cached copy cannot go stale in any way the user could observe.
     */
    @Nullable
    private StoreEntity loadedStore;

    /**
     * The configured arrival radius, read once when the sheet opens.
     *
     * <p>Read once rather than per fix, for the same reason the monitoring service does it: the radius is a
     * setting the user is not editing while this sheet is on screen, and re-reading it every few seconds
     * would be a disk query per GPS update.
     */
    private int arrivalRadiusMetres = GeoConstants.DEFAULT_ARRIVAL_RADIUS_M;

    public StoreDetailBottomSheet() {
        super(R.layout.sheet_store_detail);
    }

    /**
     * Creates the sheet for one store.
     *
     * <p>The id goes through arguments rather than a constructor parameter, because FragmentManager
     * recreates fragments through the no-argument constructor - a value held only in a field would be
     * lost on rotation, and the sheet would come back empty.
     */
    public static StoreDetailBottomSheet newInstance(long storeId) {
        StoreDetailBottomSheet sheet = new StoreDetailBottomSheet();
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

        loading = view.findViewById(R.id.store_detail_loading);
        contentGroup = view.findViewById(R.id.group_store_detail_content);
        statusChip = view.findViewById(R.id.tv_store_status_chip);
        routeNumberView = view.findViewById(R.id.tv_store_detail_route_number);
        storeCodeView = view.findViewById(R.id.tv_store_detail_store_code);
        storeNameView = view.findViewById(R.id.tv_store_detail_store_name);
        areaView = view.findViewById(R.id.tv_store_detail_area);
        coordinatesView = view.findViewById(R.id.tv_store_detail_coordinates);
        completionGroup = view.findViewById(R.id.group_store_detail_completion);
        completedDateView = view.findViewById(R.id.tv_store_detail_completed_date);
        completedTimeView = view.findViewById(R.id.tv_store_detail_completed_time);
        dailyOrderView = view.findViewById(R.id.tv_store_detail_daily_order);

        editButton = view.findViewById(R.id.btn_store_detail_edit);
        deleteButton = view.findViewById(R.id.btn_store_detail_delete);
        deleteNote = view.findViewById(R.id.tv_store_detail_delete_note);

        MaterialButton closeButton = view.findViewById(R.id.btn_store_detail_close);
        closeButton.setOnClickListener(v -> dismiss());
        editButton.setOnClickListener(v -> openEditSheet());
        deleteButton.setOnClickListener(v -> confirmDelete());

        directionsButton = view.findViewById(R.id.btn_store_detail_directions);
        directionsButton.setOnClickListener(v -> openRoutePreview());

        completeButton = view.findViewById(R.id.btn_store_detail_complete);
        completeButton.setOnClickListener(v -> openCompletionDialog());
        atLocationNote = view.findViewById(R.id.tv_store_detail_at_location);

        // The layout starts in its loading state: progress bar visible, content hidden. Reading the store
        // is what switches it.
        loadStore();
        observeCompletionResult();
    }

    /**
     * Re-reads the store the moment {@link CompletionConfirmDialogFragment} settles it.
     *
     * <p><b>Version 14 fix.</b> This sheet reads the store from Room exactly once, in {@link #loadStore}.
     * That is normally fine - the store cannot change while this sheet is the only thing open - but
     * {@link #openCompletionDialog} shows a second dialog directly on top of this one, and a YES there
     * changes this exact row without this sheet doing anything to learn about it. Without this listener
     * the status chip, the completion section and the action buttons kept showing the pre-completion
     * state until the user closed the sheet and reopened it.
     *
     * <p>Registered on the parent FragmentManager - the same one {@link #openCompletionDialog} shows the
     * confirm dialog on - and scoped to this view's lifecycle, so it stops listening the moment the sheet
     * is gone.
     */
    private void observeCompletionResult() {
        getParentFragmentManager().setFragmentResultListener(
                CompletionConfirmDialogFragment.RESULT_KEY_STORE_SETTLED,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    long settledStoreId = result.getLong(
                            CompletionConfirmDialogFragment.RESULT_ARG_STORE_ID, NO_STORE_ID);
                    if (settledStoreId == storeId) {
                        loadStore();
                    }
                });
    }

    // ------------------------------------------------------------------ the action buttons

    /**
     * Shows Get directions and, for a pending store, Store completed? - both, always.
     *
     * <p><b>Version 16: this no longer waits for GPS.</b> Earlier versions asked {@code ArrivalEvaluator}
     * whether the driver was inside the arrival radius and showed the completion button only then - the
     * idea being that this sheet should ask the one right question at the one right moment. In the field
     * that made the button disappear, sometimes for good, for exactly the drivers who needed it most: weak
     * signal in a covered market, a fix that never arrives at all, or a fix that lands a few metres outside
     * a radius that GPS jitter alone can explain. "No delay, no signal, no button" was worse than the false
     * completion the gate was built to prevent.
     *
     * <p><b>The actual safety net is unchanged.</b> {@link #openCompletionDialog} still shows the same YES
     * / NO confirmation, and that dialog - not this sheet's arrival check - is the one gate nothing in the
     * app is counted without (see {@code CompletionConfirmDialogFragment}). This method only decides
     * whether the button asking that question is on screen; it decides nothing about whether the answer is
     * trusted.
     */
    private void applyActionButtons() {
        if (directionsButton == null || completeButton == null || atLocationNote == null) {
            return;
        }
        if (loadedStore == null) {
            return;
        }

        // Only offered for a store that is still pending. A completed store has nothing to confirm, and
        // its completion is undone from the Completion Detail screen instead.
        completeButton.setVisibility(loadedStore.isCompleted() ? View.GONE : View.VISIBLE);
        directionsButton.setVisibility(View.VISIBLE);
        // Used to explain why Get directions had disappeared. Nothing disappears any more, so there is
        // nothing left for it to explain.
        atLocationNote.setVisibility(View.GONE);
    }

    /**
     * Opens the completion dialog - the same one the Navigation Active sheet opens.
     *
     * <p>The distance travels with it as evidence, when a usable fix happens to be available - but a
     * missing or poor fix is not a reason to withhold the button (see {@link #applyActionButtons}), so it
     * is read best-effort and the dialog is opened either way.
     *
     * <p><b>This sheet closes first, for the same reason it does before Edit and Get Directions.</b> Two
     * stacked sheets are a state the user cannot reason about - and here it matters more than usual: the
     * completion dialog is the one screen in the app that writes a permanent history row, and it deserves
     * the user's full attention rather than sharing the screen with the details sheet sitting behind it.
     */
    private void openCompletionDialog() {
        if (!isAdded() || loadedStore == null) {
            return;
        }

        FragmentManager fragmentManager = getParentFragmentManager();
        if (fragmentManager.findFragmentByTag(CompletionConfirmDialogFragment.TAG) != null) {
            return;
        }

        double distance = Double.NaN;
        Context context = getContext();
        if (context != null) {
            LocationFix fix = AlphaRouteApp.from(context).locationProvider().currentFix().getValue();
            if (fix != null && fix.hasUsableCoordinates()) {
                distance = DistanceCalculator.distanceMetres(
                        fix.latitude(), fix.longitude(),
                        loadedStore.latitude, loadedStore.longitude);
            }
        }

        // Captured before dismiss(), which tears this fragment's view down - loadedStore.id is a plain
        // field and survives that, but storeId is the whole reason this sheet exists, so it is read
        // explicitly rather than relied upon to still be there a line later.
        long storeId = loadedStore.id;
        dismiss();

        CompletionConfirmDialogFragment.newInstance(storeId, distance)
                .show(fragmentManager, CompletionConfirmDialogFragment.TAG);
    }

    // ------------------------------------------------------------------ loading

    /**
     * Reads the store this sheet is for.
     *
     * <p>The result is delivered on the main thread by the repository, so everything below this point
     * can touch views directly.
     */
    private void loadStore() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        if (storeId == NO_STORE_ID) {
            // Opened without an id, so there is nothing to look up. Closing is more honest than showing
            // an empty sheet, and it is the same outcome as a store that has gone.
            closeWithMessage(R.string.store_missing);
            return;
        }

        AlphaRouteApp.from(context).storeRepository().findById(storeId, this::onStoreLoaded);
    }

    private void onStoreLoaded(@Nullable StoreEntity store) {
        if (!isAdded()) {
            // The sheet was dismissed while the read was in flight. Nothing to do, and nothing to tell
            // anyone - unlike the Add sheet, no data was created that the user needs to hear about.
            return;
        }

        if (store == null) {
            // The store was deleted somewhere else while its marker was still on the map, or the marker
            // outlived it by a moment. Say so, rather than showing a blank sheet with no explanation.
            closeWithMessage(R.string.store_missing);
            return;
        }

        // Cached so the completion dialog can be opened with the right distance, when one is available.
        // A store cannot move while this sheet is open.
        loadedStore = store;

        bindStore(store);
        applyActionButtons();

        if (store.isCompleted()) {
            // A second read, only for a completed store, because the date, the time and the daily order
            // live in the completion history and not on the store row.
            loadCompletion();
        } else {
            showContent();
        }
    }

    /**
     * Reads this store's completion record.
     *
     * <p>The history table, not {@code StoreEntity.completedAt}, is the source of the completion
     * timestamp. The two should agree - the completion transaction in Phase 5.3 writes both in one
     * transaction - but the history table is the tracker's record of truth, so it is the one that gets
     * displayed.
     */
    private void loadCompletion() {
        Context context = getContext();
        if (context == null) {
            showContent();
            return;
        }

        AlphaRouteApp.from(context)
                .completionRepository()
                .findForStore(storeId, this::onCompletionLoaded);
    }

    private void onCompletionLoaded(@Nullable CompletionHistoryEntity history) {
        if (!isAdded()) {
            return;
        }

        // The Completion section is revealed only when a history row was actually found - not merely
        // because the store's status says COMPLETED. A store marked completed with no history row is a
        // data inconsistency, and the honest thing to show is nothing at all rather than an invented
        // date. Never invent data.
        if (history != null) {
            bindCompletion(history);
        }

        showContent();
    }

    // ------------------------------------------------------------------ binding

    private void bindStore(@NonNull StoreEntity store) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        applyStatusChip(context, store.isCompleted());
        applyDeleteAvailability(store.isCompleted());

        // These four are already trimmed and non-null: the repository's normalise step enforces both on
        // every write, so there is nothing to clean up here.
        routeNumberView.setText(store.routeNumber);
        storeCodeView.setText(store.storeCode);
        storeNameView.setText(store.storeName);
        areaView.setText(store.area);

        // Six decimal places is roughly 11 cm - far finer than the GPS fix that produced the pin, and
        // enough that the value can be checked against another map app without rounding getting in the
        // way.
        coordinatesView.setText(context.getString(
                R.string.coordinates_format, store.latitude, store.longitude));
    }

    private void bindCompletion(@NonNull CompletionHistoryEntity history) {
        // Both come from the same timestamp, so the date and the time can never disagree about which
        // moment they describe. The timezone is the device's own, because "when did I finish this?" is
        // a question about local time.
        completedDateView.setText(DateTimeHelper.dateText(history.completionTimestamp));
        completedTimeView.setText(DateTimeHelper.timeText(history.completionTimestamp));

        dailyOrderView.setText(getString(
                R.string.store_detail_daily_order_format, history.dailySequence));

        completionGroup.setVisibility(View.VISIBLE);
    }

    /**
     * Paints the status chip.
     *
     * <p>Both the container and the text colour are set, and both are needed: the light-mode container
     * is a pale orange or green, which needs dark text on it to stay readable. Using the marker colour
     * itself as the background would have given white-on-orange at roughly 2.6:1, which is not legible
     * at this size.
     */
    private void applyStatusChip(@NonNull Context context, boolean completed) {
        statusChip.setText(completed
                ? R.string.store_status_completed
                : R.string.store_status_pending);

        statusChip.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                context, containerColorFor(completed))));
        statusChip.setTextColor(ContextCompat.getColor(context, textColorFor(completed)));
    }

    @ColorRes
    private static int containerColorFor(boolean completed) {
        return completed ? R.color.status_completed_container : R.color.status_pending_container;
    }

    @ColorRes
    private static int textColorFor(boolean completed) {
        return completed
                ? R.color.status_on_completed_container
                : R.color.status_on_pending_container;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Opens the Route Preview for this store.
     *
     * <p><b>This sheet closes first, and for the same reasons it does before Edit.</b> Two stacked sheets
     * are a state the user cannot reason about, and this sheet would be left showing values behind the
     * preview. It also matters more here than it did for Edit: the route line is drawn on the map
     * <i>underneath</i> the preview, so the preview has to be the only sheet standing.
     */
    private void openRoutePreview() {
        if (storeId == NO_STORE_ID) {
            return;
        }

        FragmentManager fragmentManager = getParentFragmentManager();
        dismiss();
        RoutePreviewBottomSheet.newInstance(storeId)
                .show(fragmentManager, RoutePreviewBottomSheet.TAG);
    }

    /**
     * Opens the Add / Edit form for this store.
     *
     * <p><b>This sheet closes first, and the order is deliberate.</b> Two bottom sheets stacked on one
     * another is a state the user has no way to reason about, and this sheet would be left showing the
     * pre-edit values behind the form. The remove is handed to the FragmentManager before the add, so
     * the two run in that order and the form lands on the map rather than on a sheet that is on its way
     * out.
     */
    private void openEditSheet() {
        if (storeId == NO_STORE_ID) {
            return;
        }

        FragmentManager fragmentManager = getParentFragmentManager();
        dismiss();
        AddEditStoreBottomSheet.newInstanceForEdit(storeId)
                .show(fragmentManager, AddEditStoreBottomSheet.TAG);
    }

    /**
     * Asks before deleting.
     *
     * <p>Deleting a store cannot be undone from the app, so it does not happen on a single tap. The
     * store's name is in the message because a confirmation that does not say what it is about to
     * destroy is not really a confirmation.
     *
     * <p><b>Version 13: the count is read before the dialog is built, and the message says what goes with
     * the store.</b> Deleting a completed store also deletes its completion records - the Dashboard's
     * lifetime count and the History tab both lose them. A confirmation that said only "this store will be
     * deleted" would be asking permission for one thing while doing two.
     *
     * <p>The read is one-shot and the dialog is built inside its callback, so the number on screen is the
     * number at the moment the user is asked - not a count captured earlier and now possibly stale.
     */
    private void confirmDelete() {
        if (storeId == NO_STORE_ID) {
            return;
        }

        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context)
                .completionRepository()
                .countForStore(storeId, count -> {
                    if (!isAdded()) {
                        // The sheet closed while the count was being read.
                        return;
                    }
                    showDeleteDialog(count);
                });
    }

    /**
     * Builds the dialog with the count in hand.
     *
     * <p>A {@code null} count - the read failed - is not a reason to skip the warning. The message falls
     * back to one that says the records will go without naming a number, because warning vaguely is better
     * than not warning.
     */
    private void showDeleteDialog(@Nullable Integer historyCount) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        String name = String.valueOf(storeNameView.getText());
        String message;

        if (historyCount == null) {
            message = getString(R.string.store_delete_confirm_with_history_unknown, name);
        } else if (historyCount > 0) {
            message = getString(R.string.store_delete_confirm_with_history,
                    name,
                    getResources().getQuantityString(
                            R.plurals.store_delete_history_count, historyCount, historyCount));
        } else {
            // Nothing recorded against it, so there is nothing extra to warn about.
            message = getString(R.string.store_delete_confirm_message, name);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.store_delete_confirm_title)
                .setMessage(message)
                .setNegativeButton(R.string.store_action_cancel, null)
                .setPositiveButton(R.string.store_action_delete, (dialog, which) -> deleteStore());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Red, because the action cannot be undone. Material has no "destructive" button style for
        // dialogs, so the colour is applied to the button directly.
        //
        // It is read from the app's own md_error colour rather than resolved from a ?attr/colorError
        // theme attribute: md_error is the value the theme's colourError role already points at, it is
        // defined for both light and dark mode, and it does not depend on which library happens to
        // declare the attribute.
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(context, R.color.md_error));
    }

    private void deleteStore() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        AlphaRouteApp.from(context).storeRepository().delete(storeId, this::onDeleteFinished);
    }

    private void onDeleteFinished(@NonNull WriteOutcome outcome) {
        if (!isAdded()) {
            return;
        }

        switch (outcome) {
            case SUCCESS:
            case NOT_FOUND:
                // Both end the same way for the user: the store is not there any more, so this sheet has
                // nothing left to describe. NOT_FOUND means it had already gone - by an earlier tap, or
                // from somewhere else - and saying "deleted" is still an accurate description of where
                // things stand.
                closeWithMessage(R.string.store_delete_done);
                break;

            case PROTECTED_BY_HISTORY:
                // Version 13: delete no longer returns this - a completed store can be deleted, and its
                // history goes with it in the same transaction. Kept as a net because WriteOutcome is a
                // shared enum: if a caller ever does get a refusal, saying so is better than the generic
                // failure message.
                Snackbar.make(requireView(), R.string.store_delete_failed, Snackbar.LENGTH_LONG).show();
                break;

            default:
                Snackbar.make(requireView(), R.string.store_delete_failed, Snackbar.LENGTH_LONG).show();
                break;
        }
    }

    /**
     * Decides whether Delete is available for this store.
     *
     * <p>A completed store has completion history, and AD-6 makes the database refuse to delete it. The
     * button is disabled so the user is told before trying rather than after a failed attempt - but the
     * repository's {@link WriteOutcome#PROTECTED_BY_HISTORY} is still handled in
     * {@link #onDeleteFinished}, because the store could be completed after this sheet was opened and a
     * disabled button is not a guarantee, only a courtesy.
     */
    private void applyDeleteAvailability(boolean completed) {
        // Version 13: delete is ALWAYS available. Until now a completed store was protected and the button
        // was disabled, which left no way at all to remove a store completed by mistake or one that has
        // closed down. The delete now takes the history with it, in one transaction.
        //
        // The button stays enabled and the note carries the warning instead: a disabled control hides the
        // rule, whereas an enabled one with the consequence written next to it tells the user what they are
        // choosing. The dialog that follows gives the exact number of records.
        deleteButton.setEnabled(true);
        deleteNote.setVisibility(completed ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ states

    /** Swaps the loading state for the content. Called exactly once, on every path that found a store. */
    private void showContent() {
        loading.setVisibility(View.GONE);
        contentGroup.setVisibility(View.VISIBLE);
    }

    /**
     * Tells the user why the sheet is closing, then closes it.
     *
     * <p>A {@link Toast} rather than a Snackbar, and the reason is specific: a Snackbar is anchored to
     * the view it was shown on, and this sheet is being dismissed in the same breath - the message would
     * disappear with the sheet before it could be read. A Toast outlives the dismiss, which is what
     * "the store you tapped is gone" needs.
     */
    private void closeWithMessage(@StringRes int messageRes) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show();
        }
        dismiss();
    }
}
