package com.cph.alpharoute.ui.completion;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.CompletionOutcome;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.util.RouteTextFormatter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Completion confirmation - screen 9 of the Phase 0.3 screen plan.
 *
 * <p><b>The single gate that turns a visit into a completion.</b> Nothing in this app is counted without
 * an explicit YES here. Not an arrival, not a notification, not a timer, not a distance that happens to
 * be small. Every other screen can be wrong and cost the user nothing; this one writes a permanent
 * history row and moves a counter, so it is the only place in the app that asks before it acts.
 *
 * <p><b>A {@link DialogFragment}, not a {@link com.google.android.material.bottomsheet.BottomSheetDialogFragment}.</b>
 * Two reasons, and the screen plan names the first: a bottom sheet can be swiped away, and this one must
 * not disappear by accident while the user is deciding. The second is rotation - a {@code DialogFragment}
 * is rebuilt by the framework with its arguments intact, which is exactly what is needed here, because
 * losing the store id mid-decision would leave the user with a dialog that cannot complete anything.
 *
 * <p><b>YES is not "record this". YES is "ask the repository to try".</b> The transaction re-reads the
 * store and can still refuse - if the store was already completed, or was deleted while this dialog sat
 * open. That refusal is a normal answer, not a crash, and the user is told which one happened.
 *
 * <p><b>Both entry points are the user's.</b> The Navigation Active sheet offers a confirm action once the
 * arrival state is ARRIVED, and the arrival notification opens this dialog directly. Nothing opens it on
 * its own - an app that threw up a confirmation dialog without being asked would be a worse driving
 * companion than one that stayed quiet.
 */
public class CompletionConfirmDialogFragment extends DialogFragment {

    /** Dialog tag, so a caller can check whether it is already showing rather than stacking two. */
    public static final String TAG = "CompletionConfirmDialogFragment";

    /**
     * Fragment Result key broadcast on the parent FragmentManager the moment a store's completion is
     * settled (successfully counted, or found to be already counted).
     *
     * <p><b>Version 14 fix.</b> {@code StoreDetailBottomSheet} reads a store from Room exactly once,
     * when it first opens, and never again - it has no live observation of that one row. When this
     * dialog is opened on top of it and the user taps YES, the sheet underneath had no way to find out
     * that anything changed, and kept showing the pre-completion state (status chip, buttons) until it
     * was closed and reopened. Firing this result is how the sheet is told to re-read the store right
     * away, without needing a new database API for a single row.
     */
    public static final String RESULT_KEY_STORE_SETTLED = "result_store_settled";

    /** The id of the store carried in {@link #RESULT_KEY_STORE_SETTLED}'s bundle. */
    public static final String RESULT_ARG_STORE_ID = "result_store_id";

    private static final String ARG_STORE_ID = "arg_store_id";

    /**
     * The distance at which arrival was detected, or {@link #NO_DISTANCE}.
     *
     * <p>Optional on purpose. The arrival notification path knows the store but not the distance, and
     * inventing one would be worse than omitting the line.
     */
    private static final String ARG_DISTANCE_METRES = "arg_distance_metres";

    /** Sentinel for "no distance known". NaN, because 0 m is a real distance and must not be conflated. */
    private static final double NO_DISTANCE = Double.NaN;

    private static final String STATE_SUBMITTING = "state_submitting";

    private TextView storeView;
    private TextView nameView;
    private TextView areaView;
    private TextView distanceView;
    private LinearProgressIndicator progress;

    private long storeId = -1L;
    private double distanceMetres = NO_DISTANCE;

    /**
     * Whether the transaction has been sent and not yet answered.
     *
     * <p>Saved across a rotation, because the transaction keeps running on a background thread while the
     * dialog is rebuilt - the repository is owned by the Application, not by this fragment. Without this
     * flag a rotation mid-write would re-enable both buttons and invite a second tap.
     */
    private boolean submitting;

    public CompletionConfirmDialogFragment() {
        // Required public no-arg constructor. Values travel as arguments, never as fields - the
        // FragmentManager recreates this class through this constructor after a process death.
    }

    /**
     * The dialog for a store, with the distance that triggered it.
     *
     * @param storeId         the store the user is about to confirm
     * @param distanceMetres  how far away the app believed they were when arrival fired, or
     *                        {@code Double.NaN} when it is not known
     */
    @NonNull
    public static CompletionConfirmDialogFragment newInstance(long storeId, double distanceMetres) {
        CompletionConfirmDialogFragment fragment = new CompletionConfirmDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_STORE_ID, storeId);
        args.putDouble(ARG_DISTANCE_METRES, distanceMetres);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * The dialog for a store, with no distance - the arrival-notification path.
     *
     * <p>A separate factory rather than a nullable {@code Double}, so the sentinel never leaks into a
     * caller's code where it could be mistaken for a real distance.
     */
    @NonNull
    public static CompletionConfirmDialogFragment newInstance(long storeId) {
        return newInstance(storeId, NO_DISTANCE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            storeId = args.getLong(ARG_STORE_ID, -1L);
            distanceMetres = args.getDouble(ARG_DISTANCE_METRES, NO_DISTANCE);
        }

        if (savedInstanceState != null) {
            submitting = savedInstanceState.getBoolean(STATE_SUBMITTING, false);
        }

        Context context = requireContext();
        View content = getLayoutInflater().inflate(R.layout.dialog_completion_confirm, null);
        bindViews(content);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.completion_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.completion_action_no, (dialog, which) -> onDeclined())
                .setPositiveButton(R.string.completion_action_yes, (dialog, which) -> onSubmit());

        Dialog dialog = builder.create();

        // The question is worth answering deliberately. A stray tap on the scrim should not silently
        // cancel a confirmation the user was in the middle of - and while the write is in flight,
        // cancelling would leave them with no idea whether it happened.
        dialog.setCanceledOnTouchOutside(false);
        if (submitting) {
            dialog.setCancelable(false);
        }

        // Restoring mid-write: the buttons must come back disabled, or a rotation would look like the
        // write never started and invite a second tap.
        if (submitting) {
            setSubmittingUi(true);
        }

        return dialog;
    }

    private void bindViews(@NonNull View content) {
        storeView = content.findViewById(R.id.tv_completion_store);
        nameView = content.findViewById(R.id.tv_completion_name);
        areaView = content.findViewById(R.id.tv_completion_area);
        distanceView = content.findViewById(R.id.tv_completion_distance);
        progress = content.findViewById(R.id.completion_progress);

        distanceView.setVisibility(hasDistance() ? View.VISIBLE : View.GONE);
        if (hasDistance()) {
            distanceView.setText(getString(
                    R.string.completion_distance_format,
                    RouteTextFormatter.distanceText((int) Math.round(distanceMetres))));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        loadStore();
        if (submitting) {
            // A rotation can land here with the write still running. Re-checking is the only way to find
            // out whether it finished while the old fragment was being torn down - the result callback
            // belonged to that fragment and is gone.
            recoverInterruptedSubmit();
        }
    }

    private boolean hasDistance() {
        return !Double.isNaN(distanceMetres);
    }

    // ------------------------------------------------------------------ the store

    /**
     * Reads the store from Room and fills the dialog in.
     *
     * <p>The id is the only thing passed in; everything shown is read fresh. The store can have been
     * renamed, moved or deleted between the arrival and this dialog, and showing a name captured at
     * arrival time would be showing something that may no longer be true (AD-4).
     */
    private void loadStore() {
        Context context = getContext();
        if (context == null || storeId <= 0L) {
            return;
        }

        AlphaRouteApp.from(context).storeRepository().findById(storeId, store -> {
            if (!isAdded() || store == null) {
                return;
            }
            render(store);
        });
    }

    private void render(@NonNull StoreEntity store) {
        storeView.setText(getString(
                R.string.completion_store_format, store.routeNumber, store.storeCode));
        nameView.setText(store.storeName);
        areaView.setText(store.area);
    }

    // ------------------------------------------------------------------ the two answers

    /**
     * The user said NO.
     *
     * <p>Nothing happens, and that is the whole behaviour. The store stays PENDING, its marker stays
     * orange, no history row is written and no counter moves. There is deliberately no "are you sure?"
     * on top of this - NO is already the safe answer, and asking twice would make the app harder to
     * dismiss than to confirm.
     */
    private void onDeclined() {
        dismissAllowingStateLoss();
    }

    /**
     * The user said YES. Disables the buttons and asks the repository to try.
     *
     * <p>Guarded on {@code submitting} rather than on the button's enabled state: a double tap can deliver
     * two clicks before the first one's UI change is drawn, and the guard has to survive that.
     */
    private void onSubmit() {
        Context context = getContext();
        if (context == null || submitting) {
            return;
        }

        submitting = true;
        setSubmittingUi(true);

        AlphaRouteApp.from(context).completionRepository().completeStore(storeId, this::onOutcome);
    }

    /**
     * The transaction's answer.
     *
     * <p><b>Three of the four outcomes close the dialog, and only one of them is a failure.</b> That
     * asymmetry is deliberate: "already counted" and "that store is gone" both mean the question is
     * settled, so re-asking would be pointless. Only {@code FAILED} - where the transaction rolled itself
     * back and the database is untouched - is worth another attempt, so only that one leaves the dialog
     * open.
     */
    private void onOutcome(@NonNull CompletionOutcome outcome) {
        // The write is done either way, so the flag clears before anything else. Leaving it set would
        // make a rotation after a failure come back disabled, with no way to retry.
        submitting = false;

        if (!isAdded()) {
            // Rotated away or dismissed while the write ran. Nothing to report to - and nothing to
            // recover, because the database already holds the truth and the screens read it from there.
            return;
        }

        switch (outcome.status()) {
            case SUCCESS:
                // The drive is over the moment the store is counted. Ending the session here is what stops
                // the app offering the same store again, and it stops the foreground service and clears
                // the persisted reference (AD-10) in one call.
                endNavigationSession();
                toast(getString(R.string.completion_result_success_format, outcome.dailySequence()));
                notifyStoreSettled();
                dismissAllowingStateLoss();
                break;

            case ALREADY_COUNTED:
                endNavigationSession();
                toast(getString(R.string.completion_result_already));
                notifyStoreSettled();
                dismissAllowingStateLoss();
                break;

            case STORE_NOT_FOUND:
                toast(getString(R.string.completion_result_store_missing));
                dismissAllowingStateLoss();
                break;

            case FAILED:
            default:
                // Nothing was written - the transaction rolled back. So the honest thing is to put the
                // dialog back the way it was and let the user try again.
                setSubmittingUi(false);
                toast(getString(R.string.completion_result_failed));
                break;
        }
    }

    /**
     * Re-checks the store after a rotation landed mid-write.
     *
     * <p><b>Why a database read and not a saved result.</b> The transaction ran on a background thread
     * owned by the Application, and its callback was addressed to the fragment that has just been
     * destroyed. That callback is gone, so the only surviving record of what happened is the database
     * itself - which is the source of truth anyway (AD-4). If the store now reads COMPLETED, the write
     * succeeded; if it is still PENDING, it did not, and the user can try again.
     *
     * <p>The narrow case this cannot settle: the write committed and the store was then somehow flipped
     * back. Nothing in the app can do that, and if it could, the history row would still be there.
     */
    private void recoverInterruptedSubmit() {
        Context context = getContext();
        if (context == null || storeId <= 0L) {
            return;
        }

        AlphaRouteApp.from(context).storeRepository().findById(storeId, store -> {
            if (!isAdded() || store == null) {
                return;
            }
            if (store.isCompleted()) {
                submitting = false;
                endNavigationSession();
                toast(getString(R.string.completion_result_already));
                dismissAllowingStateLoss();
            } else {
                // The write never landed. Hand the dialog back to the user rather than leaving a
                // progress bar running for something that is not happening.
                submitting = false;
                setSubmittingUi(false);
            }
        });
    }

    /**
     * Tells whoever is listening (the Store Details sheet, when this dialog was opened from it) that
     * this store's status just changed, so it can re-read the row instead of going on showing what was
     * true before YES was tapped.
     *
     * <p>Fired only for the two outcomes above where the store really is COMPLETED afterwards. A
     * {@code getParentFragmentManager()} result is used rather than a direct callback reference,
     * because that is the same FragmentManager {@code StoreDetailBottomSheet} used to show this dialog
     * in the first place, and a result survives this fragment being destroyed before the listener reads
     * it.
     */
    private void notifyStoreSettled() {
        Bundle result = new Bundle();
        result.putLong(RESULT_ARG_STORE_ID, storeId);
        getParentFragmentManager().setFragmentResult(RESULT_KEY_STORE_SETTLED, result);
    }

    /**
     * Ends the navigation session, because the drive it was for is now counted.
     *
     * <p>Stopping here is what keeps the app from offering the same store twice: the session's whole
     * purpose is to watch for arrival at a store that is not yet done, and a completed store has no
     * arrival left to watch for. It also clears the persisted reference (AD-10), so a restart will not
     * offer to resume a drive that is over.
     */
    private void endNavigationSession() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        AlphaRouteApp.from(context).navigationSessionManager().stop(context);
    }

    // ------------------------------------------------------------------ state

    /** Disables both buttons and shows the progress bar - or the reverse. */
    private void setSubmittingUi(boolean busy) {
        if (progress != null) {
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        }

        Dialog dialog = getDialog();
        if (dialog instanceof AlertDialog) {
            // The buttons belong to the dialog frame, not to our content view, so they are reached
            // through the dialog. Null before the dialog is shown, hence the guard.
            AlertDialog alertDialog = (AlertDialog) dialog;
            Button positive = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            Button negative = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (positive != null) {
                positive.setEnabled(!busy);
            }
            if (negative != null) {
                negative.setEnabled(!busy);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SUBMITTING, submitting);
    }

    /**
     * A Toast, not a Snackbar.
     *
     * <p>Every message this dialog shows arrives at the moment the dialog is dismissing, so there is no
     * view left to attach a Snackbar to - and the result has to be visible whether or not the user is
     * still looking at the screen the dialog was opened from.
     */
    private void toast(@NonNull String message) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
