package com.cph.alpharoute.ui.dashboard;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.MainActivity;
import com.cph.alpharoute.R;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.StoreCandidate;
import com.cph.alpharoute.domain.SuggestionResult;
import com.cph.alpharoute.domain.TrackerSnapshot;
import com.cph.alpharoute.navigation.NavigationLauncher;
import com.cph.alpharoute.ui.common.StatCardView;
import com.cph.alpharoute.ui.route.OptimizedRouteFragment;
import com.cph.alpharoute.util.DateTimeHelper;
import com.cph.alpharoute.util.RouteTextFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Dashboard tab - the seven progress cards (Phase 6.1, live).
 *
 * <p><b>Every figure here is real.</b> The Phase 1.3 version of this screen showed fixed placeholder
 * values so the card layout could be checked; this phase wires it to Room. The placeholder strings, the
 * notice card and the code that fed them are gone, not hidden - a screen that could still show invented
 * numbers would be worse than one that showed nothing.
 *
 * <p><b>The Fragment does no arithmetic.</b> It observes one {@link TrackerSnapshot} and copies seven
 * fields onto seven cards. All the counting is in {@code TrackerCalculator}, which is pure Java and
 * tested on the JVM (AD-11) - so the numbers can be verified without a device, which matters because a
 * wrong count is invisible: it just looks like a count.
 *
 * <p><b>Nothing is stored.</b> The snapshot is recomputed from the rows on every change, so a completion
 * recorded in Phase 5.3 turns up here with no wiring between the two screens. Room's {@code LiveData}
 * re-runs the queries when the tables change, and the ViewModel recomputes.
 *
 * <p>See the no-arg constructor note in this class's Phase 1.2 form: FragmentManager recreates fragments
 * through a no-argument constructor, so the layout id is passed to {@code super(...)} from that
 * constructor rather than from a factory method.
 */
public class DashboardFragment extends Fragment {

    /** Em dash, for "no completion yet". A blank card reads as a loading failure. */
    private static final String NONE = "—";

    private StatCardView cardRound;
    private StatCardView cardRoundsFinished;
    private StatCardView cardToday;
    private StatCardView cardYesterday;
    private StatCardView cardOverall;
    private StatCardView cardRemaining;
    private StatCardView cardTotal;
    private StatCardView cardPercentage;
    private StatCardView cardLatest;
    private LinearProgressIndicator loadingIndicator;
    private View emptyCard;
    private View roundCompleteCard;

    private View suggestionCard;
    private TextView suggestionStoreView;
    private TextView suggestionDetailView;
    private TextView suggestionNoteView;
    private View suggestionActions;
    private MaterialButton navigateButton;
    private MaterialButton chooseButton;
    private MaterialButton planRouteButton;

    private DashboardViewModel viewModel;

    public DashboardFragment() {
        super(R.layout.fragment_dashboard);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cardRound = view.findViewById(R.id.card_round);
        cardRoundsFinished = view.findViewById(R.id.card_rounds_finished);
        cardToday = view.findViewById(R.id.card_today);
        cardYesterday = view.findViewById(R.id.card_yesterday);
        cardOverall = view.findViewById(R.id.card_overall);
        cardRemaining = view.findViewById(R.id.card_remaining);
        cardTotal = view.findViewById(R.id.card_total);
        cardPercentage = view.findViewById(R.id.card_percentage);
        cardLatest = view.findViewById(R.id.card_latest);
        loadingIndicator = view.findViewById(R.id.dashboard_loading);
        emptyCard = view.findViewById(R.id.card_dashboard_empty);
        roundCompleteCard = view.findViewById(R.id.card_round_complete);

        suggestionCard = view.findViewById(R.id.card_suggestion);
        suggestionStoreView = view.findViewById(R.id.tv_suggestion_store);
        suggestionDetailView = view.findViewById(R.id.tv_suggestion_detail);
        suggestionNoteView = view.findViewById(R.id.tv_suggestion_note);
        suggestionActions = view.findViewById(R.id.group_suggestion_actions);
        navigateButton = view.findViewById(R.id.btn_suggestion_navigate);
        chooseButton = view.findViewById(R.id.btn_suggestion_choose);

        // The whole-route planner (8.4). A pushed screen rather than a tab: it is a step taken from here,
        // and the way out is back.
        planRouteButton = view.findViewById(R.id.btn_plan_route);
        planRouteButton.setOnClickListener(v -> openRoutePlanner());

        // Scoped to this Fragment's view lifecycle, so it is cleared when the view goes - a ViewModel
        // holding an observer that points at a dead view is the classic leak.
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        // getViewLifecycleOwner(), never `this`: the Fragment outlives its view across a rotation, and an
        // observer bound to the Fragment would fire against views that no longer exist.
        viewModel.tracker().observe(getViewLifecycleOwner(), this::render);
        viewModel.suggestion().observe(getViewLifecycleOwner(), this::renderSuggestion);
    }

    // ------------------------------------------------------------------ the suggestion

    /**
     * Renders the next-store card.
     *
     * <p><b>Three states, one card.</b> A suggested store, the round finished, and no stores at all. The
     * card is hidden only before the first emission, so the user never sees it pop in and out - it is
     * either showing something true or not there yet.
     *
     * <p><b>The actions follow the state.</b> Navigate and Choose another need a store; the round-complete
     * state offers Home and the next round instead. Showing a Navigate button with nothing to navigate to
     * would be the kind of dead control the project keeps refusing to build.
     */
    private void renderSuggestion(@Nullable SuggestionResult suggestion) {
        if (suggestionCard == null) {
            return;
        }

        if (suggestion == null) {
            suggestionCard.setVisibility(View.GONE);
            return;
        }

        suggestionCard.setVisibility(View.VISIBLE);

        switch (suggestion.status()) {
            case SUGGESTED:
                renderSuggested(suggestion);
                break;

            case ROUTE_COMPLETE:
                renderRoundComplete();
                break;

            case NO_STORES:
            default:
                renderNoStores();
                break;
        }
    }

    private void renderSuggested(@NonNull SuggestionResult suggestion) {
        suggestionStoreView.setText(suggestion.store().label());
        suggestionDetailView.setText(detailText(suggestion));

        // Only claimed when it is true. The note would be a lie on a suggestion made without a position.
        suggestionNoteView.setVisibility(suggestion.hasDistance() ? View.VISIBLE : View.GONE);

        suggestionActions.setVisibility(View.VISIBLE);

        // Re-shown explicitly. The round-complete state hides this button when Home is unset, and without
        // this line the card would come back from that state with the primary action still missing.
        navigateButton.setVisibility(View.VISIBLE);
        navigateButton.setText(R.string.suggestion_action_navigate);
        navigateButton.setOnClickListener(v -> navigateTo(suggestion));

        chooseButton.setVisibility(View.VISIBLE);
        chooseButton.setText(R.string.suggestion_action_choose);
        chooseButton.setOnClickListener(v -> showChooseDialog());
    }

    /**
     * The round is finished - a success, and the card says so.
     *
     * <p>It offers Home when Home is set, because Home is where the route finishes; when it is not set it
     * says so rather than leaving the user with nothing. Both cases also offer the next round, since that is
     * the only thing left to do.
     */
    private void renderRoundComplete() {
        suggestionStoreView.setText(R.string.suggestion_route_complete);

        // Asked of the ViewModel, which is the object that actually observes the settings row. Reading
        // observe().getValue() here would return null until something else had observed it, and the first
        // render can arrive from the stores source before the settings row has been delivered.
        boolean homeSet = viewModel.hasHome();

        suggestionDetailView.setText(homeSet
                ? R.string.suggestion_route_complete_home
                : R.string.suggestion_route_complete_no_home);
        suggestionNoteView.setVisibility(View.GONE);
        suggestionActions.setVisibility(View.VISIBLE);

        // GONE, not disabled: there is no Home to go to, and a greyed button would suggest there was.
        navigateButton.setVisibility(homeSet ? View.VISIBLE : View.GONE);
        if (homeSet) {
            navigateButton.setText(R.string.suggestion_route_complete_action_home);
            navigateButton.setOnClickListener(v -> navigateHome());
        }

        chooseButton.setVisibility(View.VISIBLE);
        chooseButton.setText(R.string.suggestion_route_complete_action_next_round);
        chooseButton.setOnClickListener(v -> openSettingsTab());
    }

    private void renderNoStores() {
        suggestionStoreView.setText(R.string.suggestion_no_stores);
        suggestionDetailView.setVisibility(View.GONE);
        suggestionNoteView.setVisibility(View.GONE);
        // Nothing to navigate to and nothing to choose from.
        suggestionActions.setVisibility(View.GONE);
    }

    /**
     * The detail line: how far, and why this store.
     *
     * <p>The distance is omitted when there is none rather than shown as a zero. A suggestion made without
     * a position has no distance, and "0 m away" would be a claim that the user is standing in the shop.
     */
    @NonNull
    private String detailText(@NonNull SuggestionResult suggestion) {
        String reason = getString(reasonLabel(suggestion.reason()));
        if (!suggestion.hasDistance()) {
            return getString(R.string.suggestion_detail_reason_only_format, reason);
        }
        return getString(
                R.string.suggestion_detail_format,
                RouteTextFormatter.distanceText((int) Math.round(suggestion.distanceMetres())),
                reason);
    }

    @StringRes
    private static int reasonLabel(@NonNull SuggestionResult.Reason reason) {
        switch (reason) {
            case ROAD_DISTANCE:
                return R.string.suggestion_reason_road_distance;
            case HOME_AWARE:
                return R.string.suggestion_reason_home_aware;
            case FROM_HOME:
                return R.string.suggestion_reason_from_home;
            case NO_REFERENCE:
                return R.string.suggestion_reason_no_reference;
            case ONLY_ONE_LEFT:
                return R.string.suggestion_reason_only_one;
            case NEAREST:
            default:
                return R.string.suggestion_reason_nearest;
        }
    }

    // ------------------------------------------------------------------ the suggestion's actions

    /**
     * Hands the suggested store to the navigation app.
     *
     * <p>The same {@code NavigationLauncher} the Route Preview and the navigation sheet use, so there is
     * one place that knows the Google Maps URI - and one place that reports when no navigation app is
     * installed.
     */
    private void navigateTo(@NonNull SuggestionResult suggestion) {
        if (!suggestion.hasStore() || !isAdded()) {
            return;
        }
        StoreCandidate store = suggestion.store();
        boolean launched = NavigationLauncher.launch(
                requireContext(), store.latitude(), store.longitude());

        if (!launched) {
            Snackbar.make(requireView(), R.string.suggestion_no_navigation_app, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private void navigateHome() {
        // The coordinates come from the ViewModel, which holds the settings row it observes - see the note
        // in renderRoundComplete on why reading them here would be unreliable.
        GeoCoordinate home = viewModel.homeCoordinate();
        if (home == null) {
            return;
        }

        boolean launched = NavigationLauncher.launch(
                requireContext(), home.latitude(), home.longitude());
        if (!launched) {
            Snackbar.make(requireView(), R.string.suggestion_no_navigation_app, Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    /**
     * The manual override (7.3): the user picks the store instead of the strategy.
     *
     * <p>A list rather than a map picker, because the stores are already ordered nearest-first and the
     * question is "which of these", not "where".
     */
    private void showChooseDialog() {
        if (!isAdded()) {
            return;
        }

        List<StoreCandidate> pending = viewModel.pendingCandidates();
        if (pending.isEmpty()) {
            Snackbar.make(requireView(), R.string.suggestion_choose_none_pending, Snackbar.LENGTH_LONG)
                    .show();
            return;
        }

        String[] labels = new String[pending.size()];
        for (int i = 0; i < pending.size(); i++) {
            labels[i] = pending.get(i).label();
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.suggestion_choose_title)
                .setItems(labels, (dialog, which) -> viewModel.chooseStore(pending.get(which).id()))
                // "Use the suggestion" is how the override is undone. Without it, a user who picked the
                // wrong store by hand would have no way back to the strategy's choice.
                .setNeutralButton(R.string.suggestion_choose_action_use_strategy,
                        (dialog, which) -> viewModel.clearOverride())
                .setNegativeButton(R.string.suggestion_choose_cancel, null)
                .show();
    }

    /**
     * Moves to the Settings tab, where the next round begins.
     *
     * <p>A tab switch rather than a second Start-round button: the action exists in one place, and a copy
     * here would be a second path to a change that resets every store.
     */
    private void openSettingsTab() {
        if (isAdded() && requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showSettingsTab();
        }
    }

    /**
     * Opens the whole-route planner.
     *
     * <p>Pushed rather than switched to, because it is a step taken from the Dashboard - and the way out is
     * back, which is where the user was.
     */
    private void openRoutePlanner() {
        if (!isAdded() || !(requireActivity() instanceof MainActivity)) {
            return;
        }
        ((MainActivity) requireActivity()).push(
                OptimizedRouteFragment.newInstance(),
                OptimizedRouteFragment.TAG,
                R.string.title_optimized_route);
    }

    /**
     * Copies one snapshot onto the cards.
     *
     * <p>Every card is written on every emission, including the ones that have not changed. Writing only
     * the differences would need a stored copy of the previous snapshot to compare against, which is a
     * second source of truth for values that are already cheap to set - and the one time the comparison
     * got it wrong the card would be silently stale.
     */
    private void render(@NonNull TrackerSnapshot snapshot) {
        // The first emission has arrived, so the screen knows something real. Hiding the bar here rather
        // than on a timer is what makes it honest: it stays up for exactly as long as the data is unknown.
        loadingIndicator.setVisibility(View.GONE);

        cardRound.setValue(String.valueOf(snapshot.currentRound()));
        cardRoundsFinished.setValue(String.valueOf(snapshot.roundsFinished()));
        cardToday.setValue(String.valueOf(snapshot.completedToday()));
        cardYesterday.setValue(String.valueOf(snapshot.completedYesterday()));
        cardOverall.setValue(String.valueOf(snapshot.overallCompleted()));
        cardRemaining.setValue(String.valueOf(snapshot.remainingInRound()));
        cardTotal.setValue(String.valueOf(snapshot.totalStores()));
        cardPercentage.setValue(snapshot.roundPercentageText());
        cardLatest.setValue(latestText(snapshot));

        // Only when there is a route worth planning. One stop left is the suggestion card's job, and zero
        // is the round-complete message's.
        planRouteButton.setVisibility(snapshot.remainingInRound() >= 2 ? View.VISIBLE : View.GONE);

        // The empty state replaces the space the Version-7 suggestion card will take. Shown only when
        // there is not a single store - the screen plan's own empty state.
        emptyCard.setVisibility(snapshot.hasStores() ? View.GONE : View.VISIBLE);

        // Only when there is something to say: a route with no stores is not a finished round.
        roundCompleteCard.setVisibility(snapshot.isRoundComplete() ? View.VISIBLE : View.GONE);
    }

    /**
     * The "Latest completed" value: the store code and the time it was completed.
     *
     * <p>An em dash when nothing has been completed yet. The screen plan asks for exactly that, and the
     * reason is worth stating: an empty string would leave the card looking broken, and "0" would be a
     * time that does not exist.
     */
    @NonNull
    private String latestText(@NonNull TrackerSnapshot snapshot) {
        if (!snapshot.hasLatest()) {
            return NONE;
        }
        return getString(
                R.string.dashboard_latest_format,
                snapshot.latestStoreCode(),
                DateTimeHelper.timeText(snapshot.latestCompletionTimestamp()));
    }
}
