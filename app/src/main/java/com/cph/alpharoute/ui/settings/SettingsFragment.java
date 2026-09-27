package com.cph.alpharoute.ui.settings;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.BuildConfig;
import com.cph.alpharoute.MainActivity;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.auth.AuthRepository;
import com.cph.alpharoute.data.backup.BackupContents;
import com.cph.alpharoute.data.backup.BackupValidator;
import com.cph.alpharoute.data.backup.ImportMode;
import com.cph.alpharoute.data.db.AppSettingEntity;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.data.repository.BackupRepository;
import com.cph.alpharoute.data.repository.WriteOutcome;
import com.cph.alpharoute.data.sync.CloudSyncRepository;
import com.cph.alpharoute.domain.ArrivalRadiusValidator;
import com.cph.alpharoute.domain.AuthUser;
import com.cph.alpharoute.domain.CloudSyncSummary;
import com.cph.alpharoute.domain.ConsistencyChecker;
import com.cph.alpharoute.domain.RoundCalculator;
import com.cph.alpharoute.domain.RoundProgress;
import com.cph.alpharoute.util.DateKeyHelper;
import com.cph.alpharoute.util.DateTimeHelper;
import com.cph.alpharoute.util.GeoConstants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Settings - screen 12 of the Phase 0.3 screen plan.
 *
 * <p>Replaces the Phase 1.2 placeholder. It holds the two settings that actually exist in
 * {@link AppSettingEntity} - Home and the arrival radius - plus the app's version.
 *
 * <p><b>Home is shown here but chosen elsewhere.</b> The row reports where Home is, or that it is not
 * set, and its action pushes the Home Setup screen. Home needs a map to be chosen on, and this screen is
 * a list of settings; mixing the two would put a map inside a settings card.
 *
 * <p><b>The radius is the only field in the app the user types a number into.</b> Its parsing and range
 * checking live in {@link ArrivalRadiusValidator}, which is pure Java and unit-tested, rather than in a
 * click listener where nothing could reach them. That matters because a radius that came out as zero
 * would mean "stand exactly on the pin" - the app would look like it worked and never offer to complete
 * anything.
 *
 * <p><b>Everything here observes the settings row.</b> Room re-runs the query whenever the row changes,
 * so a Home set on the Home Setup screen is reflected here the moment the user comes back, with nothing
 * to hand over in between.
 */
public class SettingsFragment extends Fragment {

    private TextView homeValueView;
    private MaterialButton homeActionButton;
    private TextInputLayout arrivalRadiusLayout;
    private TextInputEditText arrivalRadiusInput;
    private MaterialButton radiusSaveButton;

    /**
     * Whether the radius field still needs filling in from the settings row.
     *
     * <p>False after a rotation, because the framework has already restored whatever the user typed and
     * filling the field in again would throw it away.
     */
    private boolean radiusNeedsPrefill;

    /**
     * "The map's marker count is not known from here."
     *
     * <p>Settings has no access to the Map tab's {@code MapMarkerController}, so the marker-count check is
     * skipped rather than guessed. A zero would report a mismatch against every store, and a false alarm
     * in a checker is worse than a missing check - the user would learn to ignore it.
     */
    private static final int UNKNOWN_MARKER_COUNT = -1;

    private TextView roundCurrentView;
    private TextView roundProgressView;
    private TextView roundFinishedView;
    private TextView roundNoteView;
    private MaterialButton startRoundButton;

    /**
     * The three live queries the round panel is built from, held so the value read back is the value that
     * was observed.
     *
     * <p><b>This is not tidiness - it is the fix for a real bug.</b> {@code observeRound} used to write
     * {@code app.storeRepository().observeAll().observe(...)} and {@code refreshRound} used to read
     * {@code app.storeRepository().observeAll().getValue()}. Those are <b>two different objects</b>: Room
     * generates every {@code LiveData}-returning query as
     * {@code getInvalidationTracker().createLiveData(...)}, which builds a <i>new</i> LiveData on every
     * call. So the instance being read had never been observed, {@code getValue()} was {@code null} every
     * time, {@code RoundCalculator.compute(null, …)} returned {@code RoundProgress(1, 0, 0)}, and the panel
     * read <b>"Round 1 in progress · 0 of 0 stores visited"</b> for ever - with "Start next round"
     * permanently disabled - no matter how many stores the user had.
     *
     * <p>The lesson is the one §8 already records as having "cost three bugs in one afternoon":
     * {@code LiveData.getValue()} is {@code null} until something observes <i>that instance</i>. Either
     * observe it here and read it back, or use a one-shot read. Never call the accessor twice.
     */
    @Nullable
    private LiveData<AppSettingEntity> roundSettings;
    @Nullable
    private LiveData<List<StoreEntity>> roundStores;
    @Nullable
    private LiveData<List<CompletionHistoryEntity>> roundHistory;

    // ------------------------------------------------------------------ cloud (Version 19)

    private TextView cloudStatusView;
    private MaterialButton cloudSaveButton;
    private MaterialButton cloudRetrieveButton;
    private LinearProgressIndicator cloudProgress;

    /**
     * Whether somebody is signed in.
     *
     * <p>Held rather than re-read, because the buttons' enabled state depends on it and it is set from the
     * auth observer - which is the single source of truth about who is signed in.
     */
    private boolean cloudSignedIn;

    /**
     * Whether a cloud operation is in flight.
     *
     * <p>Both buttons go dead while one is running. A second tap during a write is how two saves
     * interleave, and there is no sensible answer to which one won.
     */
    private boolean cloudBusy;

    private LinearLayout accountSignedInGroup;
    private LinearLayout accountSignedOutGroup;
    private TextView accountNameView;
    private TextView accountEmailView;
    private TextView accountExplanationView;
    private MaterialButton accountSignInButton;
    private MaterialButton accountSignOutButton;

    private TextView backupResultView;
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    private static final String TAG = "AlphaRouteSettings";

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        homeValueView = view.findViewById(R.id.tv_settings_home_value);
        homeActionButton = view.findViewById(R.id.btn_settings_home_action);
        arrivalRadiusLayout = view.findViewById(R.id.til_settings_arrival_radius);
        arrivalRadiusInput = view.findViewById(R.id.et_settings_arrival_radius);
        radiusSaveButton = view.findViewById(R.id.btn_settings_radius_save);

        TextView versionView = view.findViewById(R.id.tv_settings_version);
        versionView.setText(getString(R.string.app_version_format, BuildConfig.VERSION_NAME));

        radiusNeedsPrefill = savedInstanceState == null;

        homeActionButton.setOnClickListener(v -> openHomeSetup());
        radiusSaveButton.setOnClickListener(v -> onSaveRadiusClicked());

        // The consistency check (Phase 6.5). Deliberately a tap rather than an automatic run: it reads both
        // whole tables, and a check that ran on every visit would cost a full scan for an answer that
        // changes only when the data does.
        TextView consistencyResult = view.findViewById(R.id.tv_settings_consistency_result);
        view.findViewById(R.id.btn_settings_consistency_run)
                .setOnClickListener(v -> runConsistencyCheck(consistencyResult));

        // The round (Version 7).
        roundCurrentView = view.findViewById(R.id.tv_settings_round_current);
        roundProgressView = view.findViewById(R.id.tv_settings_round_progress);
        roundFinishedView = view.findViewById(R.id.tv_settings_round_finished);
        roundNoteView = view.findViewById(R.id.tv_settings_round_note);
        startRoundButton = view.findViewById(R.id.btn_settings_start_round);
        startRoundButton.setOnClickListener(v -> confirmStartNewRound());

        observeSettings();
        observeRound();
        setUpBackup();
        // Cloud BEFORE account: the account observer fires immediately with the current user, and it
        // renders the cloud card from that value - so the cloud views have to exist by then, or the first
        // render is skipped and the card sits on its placeholder until something else changes.
        setUpCloud();
        setUpAccount();
    }

    // ------------------------------------------------------------------ cloud (Version 19)

    /**
     * Wires the cloud card.
     *
     * <p><b>Nothing here happens by itself.</b> The status line is read once when the screen opens, and
     * then only after an operation - not on a timer and not on every local write. The two buttons are the
     * only things that change any data, in either direction.
     */
    private void setUpCloud() {
        cloudStatusView = requireView().findViewById(R.id.tv_cloud_status);
        cloudSaveButton = requireView().findViewById(R.id.btn_cloud_save);
        cloudRetrieveButton = requireView().findViewById(R.id.btn_cloud_retrieve);
        cloudProgress = requireView().findViewById(R.id.cloud_progress);

        cloudSaveButton.setOnClickListener(v -> onSaveToCloudClicked());
        cloudRetrieveButton.setOnClickListener(v -> onRetrieveFromCloudClicked());
    }

    /**
     * Brings the cloud card in line with who is signed in.
     *
     * <p>Signed out, there is nowhere to put the data, so both buttons are disabled and the status line
     * says why. A dead button with no explanation is worse than no button.
     */
    private void renderCloudAccount(@Nullable AuthUser user) {
        if (cloudStatusView == null) {
            return;
        }

        cloudSignedIn = user != null && user.isValid();

        if (!cloudSignedIn) {
            cloudStatusView.setText(R.string.settings_cloud_signed_out);
            setCloudBusy(false);
            return;
        }

        setCloudBusy(false);
        refreshCloudStatus();
    }

    /** Reads what the cloud holds. Only the summary fields, so this is a few hundred bytes. */
    private void refreshCloudStatus() {
        if (cloudStatusView == null || !isAdded()) {
            return;
        }

        cloudStatusView.setText(R.string.settings_cloud_checking);

        AlphaRouteApp.from(requireContext()).cloudSyncRepository().refreshStatus(status -> {
            if (!isAdded() || cloudStatusView == null) {
                return;
            }
            cloudStatusView.setText(cloudStatusText(status));
        });
    }

    /** The status line, in words. Every outcome says where the data is rather than what went wrong. */
    @NonNull
    private String cloudStatusText(@NonNull CloudSyncRepository.Status status) {
        switch (status.outcome) {
            case SUCCESS:
                return getString(
                        R.string.settings_cloud_status_format,
                        countsText(status.storeCount, status.completionCount),
                        whenText(status.exportedAt));
            case NOTHING_THERE:
                return getString(R.string.settings_cloud_nothing_there);
            case NOT_SIGNED_IN:
                return getString(R.string.settings_cloud_signed_out);
            case UNREADABLE:
                return getString(R.string.settings_cloud_unreadable);
            default:
                // FAILED, EMPTY and INVALID all mean the cloud could not be reached or read. The status
                // line says that rather than naming an internal outcome.
                return getString(R.string.settings_cloud_unreachable);
        }
    }

    @NonNull
    private String countsText(int stores, int completions) {
        return getString(R.string.settings_cloud_counts_format, stores, completions);
    }

    /**
     * When the cloud copy was written, in the phone's own date and time format.
     *
     * <p>An absolute time rather than "2 days ago": the user is checking whether the save they just made
     * actually landed, and "just now" and "a moment ago" both look the same on a screen that might not
     * have refreshed.
     */
    @NonNull
    private String whenText(long exportedAt) {
        if (exportedAt <= 0L) {
            return getString(R.string.settings_cloud_nothing_there);
        }
        return DateTimeHelper.dateText(exportedAt) + " " + DateTimeHelper.timeText(exportedAt);
    }

    private void setCloudBusy(boolean busy) {
        cloudBusy = busy;
        if (cloudProgress != null) {
            cloudProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
        if (cloudSaveButton != null) {
            cloudSaveButton.setEnabled(cloudSignedIn && !busy);
        }
        if (cloudRetrieveButton != null) {
            cloudRetrieveButton.setEnabled(cloudSignedIn && !busy);
        }
    }

    private void snack(@NonNull String message) {
        if (isAdded()) {
            Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ save to cloud

    /**
     * Saves this phone's whole database to the cloud.
     *
     * <p><b>Asks first when the cloud already holds something</b>, because saving replaces it - and the
     * warning names what is about to be replaced rather than saying "are you sure". Replacing an empty
     * cloud needs no confirmation: there is nothing to lose.
     */
    private void onSaveToCloudClicked() {
        if (cloudBusy || !cloudSignedIn || !isAdded()) {
            return;
        }

        AlphaRouteApp.from(requireContext()).cloudSyncRepository().refreshStatus(status -> {
            if (!isAdded() || cloudBusy) {
                return;
            }
            if (!status.hasData()) {
                performCloudSave();
                return;
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_cloud_save_confirm_title)
                    .setMessage(getString(
                            R.string.settings_cloud_save_confirm_message,
                            countsText(status.storeCount, status.completionCount),
                            whenText(status.exportedAt)))
                    .setNegativeButton(R.string.settings_cloud_cancel, null)
                    .setPositiveButton(R.string.settings_cloud_save_confirm_action,
                            (dialog, which) -> performCloudSave())
                    .show();
        });
    }

    private void performCloudSave() {
        if (!isAdded()) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(requireContext());
        setCloudBusy(true);

        app.cloudSyncRepository().saveToCloud(outcome -> {
            if (!isAdded()) {
                return;
            }
            setCloudBusy(false);

            switch (outcome) {
                case SUCCESS:
                    // Read back rather than reporting what the phone thought it sent. The message then
                    // describes the cloud, which is the thing the user is checking.
                    app.cloudSyncRepository().refreshStatus(status -> {
                        if (!isAdded() || cloudStatusView == null) {
                            return;
                        }
                        cloudStatusView.setText(cloudStatusText(status));
                        snack(getString(R.string.settings_cloud_saved_format,
                                countsText(status.storeCount, status.completionCount)));
                    });
                    break;
                case EMPTY:
                    snack(getString(R.string.settings_cloud_save_empty));
                    break;
                case NOT_SIGNED_IN:
                    snack(getString(R.string.settings_cloud_signed_out_toast));
                    break;
                default:
                    snack(getString(R.string.settings_cloud_save_failed));
                    break;
            }
        });
    }

    // ------------------------------------------------------------------ retrieve from cloud

    /**
     * Brings the cloud's copy back.
     *
     * <p><b>Fetches, validates and counts first, and writes nothing until the user has seen the
     * numbers.</b> That is the whole reason this is a separate method from {@code applyFromCloud}: a
     * preview that had already written something would not be a preview.
     */
    private void onRetrieveFromCloudClicked() {
        if (cloudBusy || !cloudSignedIn || !isAdded()) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(requireContext());
        setCloudBusy(true);

        app.cloudSyncRepository().fetchFromCloud(fetched -> {
            if (!isAdded()) {
                return;
            }
            setCloudBusy(false);

            if (fetched.outcome != CloudSyncRepository.Outcome.SUCCESS
                    || fetched.contents == null
                    || fetched.summary == null) {
                snack(retrieveFailureText(fetched.outcome));
                return;
            }

            showRetrievePreview(app, fetched.contents, fetched.summary, fetched.exportedAt);
        });
    }

    @NonNull
    private String retrieveFailureText(@NonNull CloudSyncRepository.Outcome outcome) {
        switch (outcome) {
            case NOT_SIGNED_IN:
                return getString(R.string.settings_cloud_signed_out_toast);
            case NOTHING_THERE:
                return getString(R.string.settings_cloud_retrieve_nothing);
            case INVALID:
            case UNREADABLE:
                return getString(R.string.settings_cloud_retrieve_invalid);
            default:
                return getString(R.string.settings_cloud_retrieve_failed);
        }
    }

    /**
     * Shows what the cloud holds and what merging would do, then lets the user choose.
     *
     * <p>Merge is the positive button and Replace is the neutral one, so the safe answer is the easy one
     * to hit and the destructive one is deliberately in a different place. Replace asks a second time.
     */
    private void showRetrievePreview(@NonNull AlphaRouteApp app,
                                     @NonNull BackupContents contents,
                                     @NonNull CloudSyncSummary summary,
                                     long exportedAt) {

        String counts = countsText(summary.cloudStoreCount(), summary.cloudCompletionCount());
        String when = whenText(exportedAt);

        if (summary.nothingNew()) {
            // Offering Merge here would be offering an action that changes nothing. Saying so is the
            // answer, and it is a real one - the user needs to know the cloud is not ahead of them.
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_cloud_preview_title)
                    .setMessage(getString(R.string.settings_cloud_preview_nothing_new, counts, when))
                    .setPositiveButton(R.string.settings_cloud_ok, null)
                    .show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_cloud_preview_title)
                .setMessage(getString(
                        R.string.settings_cloud_preview_message,
                        counts,
                        when,
                        getString(R.string.settings_cloud_preview_extra_format,
                                summary.storesNew(), summary.completionsNew())))
                .setNegativeButton(R.string.settings_cloud_cancel, null)
                .setNeutralButton(R.string.settings_cloud_replace,
                        (dialog, which) -> confirmReplace(app, contents))
                .setPositiveButton(R.string.settings_cloud_merge,
                        (dialog, which) -> applyFromCloud(app, contents, ImportMode.MERGE))
                .show();
    }

    /** The second ask. Replacing deletes everything on this phone first. */
    private void confirmReplace(@NonNull AlphaRouteApp app, @NonNull BackupContents contents) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_cloud_replace_title)
                .setMessage(R.string.settings_cloud_replace_message)
                .setNegativeButton(R.string.settings_cloud_cancel, null)
                .setPositiveButton(R.string.settings_cloud_replace_confirm,
                        (dialog, which) -> applyFromCloud(app, contents, ImportMode.REPLACE))
                .show();
    }

    /**
     * Writes the cloud copy onto this phone.
     *
     * <p>One transaction, and a failure rolls the whole thing back - the same guarantee the file restore
     * gives, because it is the same code.
     */
    private void applyFromCloud(@NonNull AlphaRouteApp app,
                                @NonNull BackupContents contents,
                                @NonNull ImportMode mode) {

        setCloudBusy(true);

        app.cloudSyncRepository().applyFromCloud(contents, mode, outcome -> {
            if (!isAdded()) {
                return;
            }
            setCloudBusy(false);

            if (outcome == null) {
                // Rolled back. The data on this phone is exactly as it was.
                snack(getString(R.string.settings_cloud_retrieve_failed));
                return;
            }

            snack(getString(
                    R.string.settings_cloud_retrieved_format,
                    outcome.storesAdded,
                    outcome.completionsAdded,
                    outcome.storesSkipped));
        });
    }

    // ------------------------------------------------------------------ account (Version 14)

    /**
     * Wires the account card.
     *
     * <p><b>Three states, and the card decides which one shows from the observed user rather than from
     * anything cached here.</b> {@code AuthRepository.observeUser()} is the single source of truth about
     * who is signed in, and Firebase's own auth listener feeds it - so a session restored on launch, a
     * sign-in from this button and a sign-out all arrive through the same path.
     */
    private void setUpAccount() {
        accountSignedInGroup = requireView().findViewById(R.id.group_account_signed_in);
        accountSignedOutGroup = requireView().findViewById(R.id.group_account_signed_out);
        accountNameView = requireView().findViewById(R.id.tv_account_name);
        accountEmailView = requireView().findViewById(R.id.tv_account_email);
        accountExplanationView = requireView().findViewById(R.id.tv_account_explanation);
        accountSignInButton = requireView().findViewById(R.id.btn_account_sign_in);
        accountSignOutButton = requireView().findViewById(R.id.btn_account_sign_out);

        accountSignInButton.setOnClickListener(v -> beginSignIn());
        accountSignOutButton.setOnClickListener(v -> confirmSignOut());

        // One observer for both cards. Who is signed in decides whether the cloud buttons can do anything
        // at all, so the two must be rendered from the same value - reading the account state twice would
        // let the sign-in card and the cloud card disagree.
        AlphaRouteApp.from(requireContext())
                .authRepository()
                .observeUser()
                .observe(getViewLifecycleOwner(), user -> {
                    renderAccount(user);
                    renderCloudAccount(user);
                });
    }

    /** Shows the right half of the card for the current state. A null user means signed out. */
    private void renderAccount(@Nullable AuthUser user) {
        if (accountSignedInGroup == null) {
            return;
        }

        boolean signedIn = user != null && user.isValid();

        accountSignedInGroup.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        accountSignedOutGroup.setVisibility(signedIn ? View.GONE : View.VISIBLE);

        if (signedIn) {
            // A Google account can genuinely have no display name, and label() falls back to the email so
            // the heading is never blank - which meant the card printed the SAME ADDRESS TWICE, one line
            // under the other. Two identical lines read as a rendering bug rather than as a missing name.
            //
            // So when the label and the email are the same string, the email IS the heading and the second
            // line is dropped. One line that says who you are beats two lines that say it twice.
            String label = user.label();
            String email = user.email();
            boolean labelRepeatsEmail = label.equals(email);

            // The heading always shows something - label() guarantees it is never blank.
            accountNameView.setText(label);
            accountNameView.setVisibility(View.VISIBLE);

            accountEmailView.setText(email);
            accountEmailView.setVisibility(
                    email.isEmpty() || labelRepeatsEmail ? View.GONE : View.VISIBLE);
            return;
        }

        // Signed out.
        //
        // The button is ALWAYS shown, and disabled rather than hidden when sign-in cannot work yet.
        // Hiding it was the first attempt and it was wrong: the card then contained nothing but a
        // paragraph of text, so it read as an empty panel or as a missing feature - which is exactly
        // what a user reported as "why is there no profile in Settings".
        //
        // A disabled control with the reason above it tells the user what the thing IS and why it is
        // not available. A missing control tells them nothing.
        boolean configured = AlphaRouteApp.from(requireContext()).authRepository().isConfigured();
        accountSignInButton.setEnabled(configured);
        accountExplanationView.setText(configured
                ? R.string.settings_account_signed_out_explanation
                : R.string.settings_account_not_configured);
    }

    private void beginSignIn() {
        AlphaRouteApp.from(requireContext()).authRepository().signIn(requireActivity(), new AuthRepository.SignInCallback() {
            @Override
            public void onSucceeded() {
                // Nothing to do: the observed user changes and renderAccount redraws the card.
            }

            @Override
            public void onFailed(@NonNull AuthRepository.SignInFailure failure,
                                 @NonNull String detail) {
                if (!isAdded()) {
                    return;
                }
                // Each failure names the user's actual next move. Only one of them is their fault.
                switch (failure) {
                    case CANCELLED:
                        // They dismissed the picker. Saying anything at all would be noise.
                        break;
                    case NO_ACCOUNT:
                        showAccountMessage(getString(R.string.settings_account_no_account, detail));
                        break;
                    case NOT_CONFIGURED:
                        showAccountMessage(getString(R.string.settings_account_not_configured_toast));
                        break;
                    case FAILED:
                    default:
                        showAccountMessage(getString(R.string.settings_account_sign_in_failed, detail));
                        break;
                }
            }
        });
    }

    /**
     * Asks before signing out.
     *
     * <p>Signing out does <b>not</b> delete anything - the stores and the history stay on the phone. The
     * message says so, because "sign out" on a synced app reasonably raises the question.
     */
    private void confirmSignOut() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.settings_account_sign_out)
                .setMessage(R.string.settings_account_sign_out_confirm)
                .setNegativeButton(R.string.store_action_cancel, null)
                .setPositiveButton(R.string.settings_account_sign_out, (dialog, which) -> {
                    AlphaRouteApp.from(requireContext()).authRepository().signOut();
                    showAccountMessage(getString(R.string.settings_account_signed_out));
                })
                .show();
    }

    private void showAccountMessage(@NonNull String message) {
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ backup and restore

    /**
     * Wires the export and import buttons.
     *
     * <p><b>Both use the system file picker, and that is deliberate.</b> The Storage Access Framework asks
     * the user where a file should go and hands back a URI - so the app needs no storage permission, never
     * guesses at a path, and cannot write anywhere the user did not choose. On Android 10 and later a
     * hand-rolled path would not work at all.
     *
     * <p>The launchers are registered here, in {@code onViewCreated}, rather than as fields - unlike the
     * permission launchers elsewhere in the app, which must exist before the fragment is created. These are
     * contract launchers with no permission semantics, and registering them with the view means they are
     * only live while this screen is.
     */
    private void setUpBackup() {
        backupResultView = requireView().findViewById(R.id.tv_settings_backup_result);

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) {
                        exportTo(uri);
                    }
                });

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        readBackup(uri);
                    }
                });

        requireView().findViewById(R.id.btn_settings_export)
                .setOnClickListener(v -> exportLauncher.launch(suggestedFileName()));

        requireView().findViewById(R.id.btn_settings_import)
                .setOnClickListener(v -> importLauncher.launch(new String[]{"application/json"}));
    }

    /**
     * The file name offered to the picker: {@code alpharoute-backup-2026-09-27.json}.
     *
     * <p>Dated rather than numbered, so a folder of backups sorts chronologically by name and two backups
     * taken on different days cannot collide.
     */
    @NonNull
    private String suggestedFileName() {
        return "alpharoute-backup-" + DateKeyHelper.today() + ".json";
    }

    private void exportTo(@NonNull Uri uri) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        try {
            OutputStream out = context.getContentResolver().openOutputStream(uri);
            if (out == null) {
                showBackupResult(getString(R.string.settings_backup_export_failed));
                return;
            }

            AlphaRouteApp.from(context).backupRepository().exportTo(out, bytes -> {
                if (!isAdded()) {
                    return;
                }
                showBackupResult(bytes > 0
                        ? getString(R.string.settings_backup_export_done)
                        : getString(R.string.settings_backup_export_failed));
            });

        } catch (Exception e) {
            // The picker can hand back a URI the app cannot open - a provider that revoked access, or a
            // destination that no longer exists. Reported, not thrown.
            Log.e(TAG, "Could not open the export destination", e);
            showBackupResult(getString(R.string.settings_backup_export_failed));
        }
    }

    /**
     * Reads a chosen file and shows what it contains, before anything is written.
     *
     * <p><b>Nothing is restored by this method.</b> It reads, validates, and then asks - because a restore
     * can replace every store, and a confirmation that did not say what it would do would not be a
     * confirmation.
     */
    private void readBackup(@NonNull Uri uri) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                showBackupResult(getString(R.string.settings_backup_import_unreadable));
                return;
            }

            AlphaRouteApp.from(context).backupRepository().readAndValidate(in, contents -> {
                if (!isAdded()) {
                    return;
                }
                onBackupRead(contents);
            });

        } catch (Exception e) {
            Log.e(TAG, "Could not open the chosen backup", e);
            showBackupResult(getString(R.string.settings_backup_import_unreadable));
        }
    }

    private void onBackupRead(@Nullable BackupContents contents) {
        List<BackupValidator.Problem> problems = BackupRepository.validate(contents);

        if (!problems.isEmpty()) {
            // Every problem is shown, not just the first. A user with three faults should not have to fix
            // one, retry, and discover the next.
            StringBuilder message = new StringBuilder(
                    getResources().getQuantityString(
                            R.plurals.settings_backup_problems, problems.size(), problems.size()));
            for (BackupValidator.Problem problem : problems) {
                message.append('\n').append("• ").append(problem.message);
            }
            showBackupResult(message.toString());
            return;
        }

        confirmRestore(contents);
    }

    /**
     * Asks which kind of restore, and then whether to go ahead.
     *
     * <p><b>Two questions, in this order, because they are different questions.</b> "Replace or merge" is
     * about what the user wants; "are you sure" is about the consequence. Asking them the other way round
     * would make the user confirm before knowing what they were confirming.
     */
    private void confirmRestore(@NonNull BackupContents contents) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        String summary = BackupValidator.describe(contents);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.settings_backup_mode_title)
                .setMessage(getString(R.string.settings_backup_mode_message, summary))
                .setNegativeButton(R.string.settings_backup_mode_cancel, null)
                .setNeutralButton(R.string.settings_backup_mode_merge,
                        (dialog, which) -> restore(contents, ImportMode.MERGE))
                .setPositiveButton(R.string.settings_backup_mode_replace,
                        (dialog, which) -> confirmReplace(contents))
                .show();
    }

    /** The second question, for the destructive option only. */
    private void confirmReplace(@NonNull BackupContents contents) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.settings_backup_replace_title)
                .setMessage(R.string.settings_backup_replace_message)
                .setNegativeButton(R.string.settings_backup_mode_cancel, null)
                .setPositiveButton(R.string.settings_backup_replace_confirm,
                        (dialog, which) -> restore(contents, ImportMode.REPLACE))
                .show();
    }

    private void restore(@NonNull BackupContents contents, @NonNull ImportMode mode) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context).backupRepository().restore(contents, mode, outcome -> {
            if (!isAdded()) {
                return;
            }

            if (outcome == null) {
                // Rolled back. The app is exactly as it was, which is what the message promises.
                showBackupResult(getString(R.string.settings_backup_import_failed));
                return;
            }

            showBackupResult(getString(
                    R.string.settings_backup_import_done,
                    outcome.storesAdded,
                    outcome.completionsAdded,
                    outcome.storesSkipped));
        });
    }

    private void showBackupResult(@NonNull String message) {
        if (backupResultView == null) {
            return;
        }
        backupResultView.setText(message);
        backupResultView.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ the round

    /**
     * Watches the settings row, which holds the round number.
     *
     * <p>Two observers rather than one: the round's progress also depends on the stores and the history,
     * and those change without the settings row being written. Re-reading on every store change is a cheap
     * query and the only way the counts stay right while this screen is open.
     */
    private void observeRound() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(context);

        // Captured ONCE, so refreshRound reads the same instances this observes. Calling the accessor again
        // there would build fresh, unobserved LiveData and every read would come back null - see the field
        // note above. This is the whole fix.
        roundSettings = app.settingsRepository().observe();
        roundStores = app.storeRepository().observeAll();
        roundHistory = app.completionRepository().observeAllOrdered();

        // All three drive the same panel, and any one of them can arrive first, so each re-renders the lot.
        roundSettings.observe(getViewLifecycleOwner(), settings -> refreshRound());
        roundStores.observe(getViewLifecycleOwner(), stores -> refreshRound());
        roundHistory.observe(getViewLifecycleOwner(), history -> refreshRound());
    }

    /**
     * Recomputes the round panel from the three things it depends on.
     *
     * <p>Reads the values rather than being handed them, because the three observers fire independently
     * and any one of them can be the first to arrive - so whichever arrives last still has to be able to
     * see the other two. The values are already in memory (Room's own emissions), so reading them costs
     * nothing.
     *
     * <p><b>It reads the three {@code LiveData} fields, never the repository accessors.</b> The accessors
     * build a new {@code LiveData} on every call, so reading through them returns {@code null} for ever -
     * see the note on {@link #roundSettings}.
     */
    private void refreshRound() {
        Context context = getContext();
        if (context == null || roundCurrentView == null) {
            return;
        }
        if (roundSettings == null || roundStores == null || roundHistory == null) {
            // observeRound has not run yet. Nothing to read, and nothing to say.
            return;
        }

        AppSettingEntity settings = roundSettings.getValue();
        List<StoreEntity> stores = roundStores.getValue();
        List<CompletionHistoryEntity> history = roundHistory.getValue();

        // All three have to have arrived. Drawing from a partial answer is what produced the wrong number:
        // a null store list makes RoundCalculator report "0 of 0 stores visited" on a screen that has
        // stores, which looks exactly like a broken feature rather than like data that is still loading.
        // Returning leaves the panel as it was for a moment instead of stating something untrue.
        if (settings == null || stores == null || history == null) {
            return;
        }

        int currentRound = settings.currentRound;

        RoundProgress progress = RoundCalculator.compute(stores, history, currentRound);

        roundCurrentView.setText(getString(R.string.settings_round_current_format, currentRound));

        // "0 of 0 stores visited" is technically true and reads like a broken counter. With nothing to
        // count, the honest line is that there is nothing to count - and the note below says what to do
        // about it. The Dashboard has said it this way since Version 7; this is the same sentence.
        roundProgressView.setText(progress.totalStores() == 0
                ? getString(R.string.settings_round_no_stores_yet)
                : getString(R.string.settings_round_progress_format,
                        progress.completedInRound(), progress.totalStores()));

        roundFinishedView.setText(getString(
                R.string.settings_round_finished_format, Math.max(0, currentRound - 1)));

        boolean canStart = progress.canStartNewRound();
        startRoundButton.setEnabled(canStart);

        // The note explains a disabled button, and disappears when there is nothing to explain.
        if (progress.totalStores() == 0) {
            roundNoteView.setText(R.string.settings_round_no_stores);
            roundNoteView.setVisibility(View.VISIBLE);
        } else if (!canStart) {
            roundNoteView.setText(getString(
                    R.string.settings_round_incomplete_format, progress.remainingInRound()));
            roundNoteView.setVisibility(View.VISIBLE);
        } else {
            roundNoteView.setVisibility(View.GONE);
        }
    }

    /**
     * Asks before starting the next round.
     *
     * <p>It is worth a confirmation because it changes every store at once - the whole map goes back to
     * orange. The message says the one thing the user might fear: the history is not deleted.
     */
    private void confirmStartNewRound() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.settings_round_confirm_title)
                .setMessage(R.string.settings_round_confirm_message)
                .setNegativeButton(R.string.settings_round_cancel, null)
                .setPositiveButton(R.string.settings_round_confirm_action,
                        (dialog, which) -> startNewRound())
                .show();
    }

    /**
     * Begins the next round.
     *
     * <p>The repository re-checks that the round is complete inside its transaction, so this does not rely
     * on the button's enabled state - which was decided from an earlier read and could already be out of
     * date if a store was added in the meantime.
     */
    private void startNewRound() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context).completionRepository().startNewRound(outcome -> {
            if (!isAdded()) {
                return;
            }

            switch (outcome.status()) {
                case SUCCESS:
                    toast(getString(
                            R.string.settings_round_started_format,
                            outcome.newRound(),
                            outcome.storesReset()));
                    break;

                case ROUND_NOT_COMPLETE:
                    // The round became incomplete between the button being drawn and the tap. Saying how
                    // many are left is more useful than "failed".
                    toast(getString(
                            R.string.settings_round_incomplete_format, outcome.stillToVisit()));
                    break;

                case NO_STORES:
                    toast(getString(R.string.settings_round_no_stores));
                    break;

                case FAILED:
                default:
                    toast(getString(R.string.settings_round_failed));
                    break;
            }

            // Refreshed either way: a refusal may have been caused by a change this screen has not seen yet.
            refreshRound();
        });
    }

    private void toast(@NonNull String message) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ the consistency check

    /**
     * Reads both tables and reports whether they still agree.
     *
     * <p><b>It reports; it never repairs.</b> See {@code ConsistencyChecker} for why - briefly, a checker
     * that fixes what it finds can also destroy what it does not understand, and deleting an orphaned
     * history row would erase the only evidence that a completion happened.
     *
     * <p>The map's marker count is deliberately NOT passed in. This screen has no access to the Map tab's
     * {@code MapMarkerController}, and passing a guess would report a mismatch that is not real - so
     * {@code -1} says "unknown" and that one check is skipped. It runs on the Map tab's own visit.
     */
    private void runConsistencyCheck(@NonNull TextView resultView) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(context);

        // All three reads are one-shot, because the answer is a point-in-time report rather than something
        // to observe. The round is read the same way as the two tables - reading it from
        // observe().getValue() would depend on this screen's own observer having already fired, which is a
        // different thing from the value being available.
        app.settingsRepository().getNow(settings ->
                app.storeRepository().findAllNow(stores ->
                        app.completionRepository().findAllNow(history -> {
                            if (!isAdded()) {
                                return;
                            }

                            final int currentRound = settings == null ? 1 : settings.currentRound;

                            List<ConsistencyChecker.Finding> findings = ConsistencyChecker.check(
                                    stores, history, UNKNOWN_MARKER_COUNT, currentRound);

                            resultView.setVisibility(View.VISIBLE);
                            resultView.setText(describeFindings(findings));
                        })));
    }

    /**
     * Turns the findings into one readable line.
     *
     * <p>A clean report says so in words rather than showing an empty box - "Everything agrees" is the
     * answer to the question the user asked, and a blank area would read as a failure to run.
     */
    @NonNull
    private String describeFindings(@NonNull List<ConsistencyChecker.Finding> findings) {
        if (findings.isEmpty()) {
            return getString(R.string.settings_consistency_clean);
        }

        StringBuilder text = new StringBuilder(
                getResources().getQuantityString(
                        R.plurals.settings_consistency_issues,
                        findings.size(),
                        findings.size()));

        for (ConsistencyChecker.Finding finding : findings) {
            text.append('\n')
                    .append(getString(labelFor(finding.issue())))
                    .append(": ")
                    .append(finding.detail());
        }
        return text.toString();
    }

    /** The plain-language label for each kind of finding. */
    private static int labelFor(@NonNull ConsistencyChecker.Issue issue) {
        switch (issue) {
            case COMPLETED_WITHOUT_HISTORY:
                return R.string.settings_consistency_completed_without_history;
            case HISTORY_WITHOUT_COMPLETION:
                return R.string.settings_consistency_history_without_completion;
            case ORPHANED_HISTORY:
                return R.string.settings_consistency_orphaned_history;
            case MARKER_COUNT_MISMATCH:
            default:
                return R.string.settings_consistency_marker_mismatch;
        }
    }

    // ------------------------------------------------------------------ reading

    private void observeSettings() {
        AlphaRouteApp.from(requireContext())
                .settingsRepository()
                .observe()
                .observe(getViewLifecycleOwner(), this::applySettings);
    }

    private void applySettings(@Nullable AppSettingEntity settings) {
        if (settings == null) {
            return;
        }

        applyHome(settings);

        // Filled in once, and only when there is no restored text to preserve. The settings row emits on
        // every write - including the one this screen has just made - so setting the text every time would
        // fight whatever the user is in the middle of typing.
        if (radiusNeedsPrefill) {
            radiusNeedsPrefill = false;
            arrivalRadiusInput.setText(String.valueOf(settings.arrivalRadius));
        }
    }

    private void applyHome(@NonNull AppSettingEntity settings) {
        // isHomeSet() is the single place that decides whether Home is configured - it requires the flag
        // and both coordinates to agree. Checking the fields here instead would be a second definition of
        // the same rule, free to disagree with the first.
        if (settings.isHomeSet()) {
            homeValueView.setText(getString(
                    R.string.coordinates_format, settings.homeLatitude, settings.homeLongitude));
            homeActionButton.setText(R.string.settings_action_change_home);
        } else {
            homeValueView.setText(R.string.settings_home_not_set);
            homeActionButton.setText(R.string.settings_action_set_home);
        }
    }

    // ------------------------------------------------------------------ writing

    private void onSaveRadiusClicked() {
        // The parser reports "not usable" as null rather than as 0 or -1, so there is no way to
        // accidentally save a radius that would stop the arrival check from ever firing.
        Integer metres = ArrivalRadiusValidator.parseMetres(textOf(arrivalRadiusInput));
        if (metres == null) {
            // The bounds in the message come from GeoConstants, the same constants the validator used, so
            // the text can never promise a range the validator would refuse.
            arrivalRadiusLayout.setError(getString(
                    R.string.settings_radius_invalid_format,
                    GeoConstants.MIN_ARRIVAL_RADIUS_M,
                    GeoConstants.MAX_ARRIVAL_RADIUS_M));
            arrivalRadiusInput.requestFocus();
            return;
        }

        arrivalRadiusLayout.setError(null);
        radiusSaveButton.setEnabled(false);

        AlphaRouteApp.from(requireContext())
                .settingsRepository()
                .setArrivalRadius(metres, this::onRadiusSaved);
    }

    private void onRadiusSaved(@NonNull WriteOutcome outcome) {
        if (!isAdded()) {
            // The screen went away while the write was in flight. It is saved; there is nobody to tell.
            return;
        }

        radiusSaveButton.setEnabled(true);

        if (outcome == WriteOutcome.SUCCESS) {
            Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show();
            return;
        }

        Snackbar.make(requireView(), R.string.settings_save_failed, Snackbar.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ navigation

    /**
     * Asks the Activity to push Home Setup.
     *
     * <p>A cast to {@link MainActivity} rather than a listener interface, and the reason is specific: this
     * app has exactly one Activity (AD-1), so the interface would have exactly one implementation. That
     * is indirection that hides the dependency without removing it. If a second Activity ever appears,
     * this is the one method to change.
     */
    private void openHomeSetup() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).openHomeSetup();
        }
    }

    @Nullable
    private static String textOf(@Nullable TextView view) {
        return view == null || view.getText() == null ? null : view.getText().toString();
    }
}
