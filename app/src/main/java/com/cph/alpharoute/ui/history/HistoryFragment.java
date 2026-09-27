package com.cph.alpharoute.ui.history;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.RoundGrouper;
import com.cph.alpharoute.domain.RoundReport;
import com.cph.alpharoute.MainActivity;
import com.cph.alpharoute.R;
import com.cph.alpharoute.domain.HistoryDay;
import com.cph.alpharoute.domain.HistoryGrouper;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Collections;
import java.util.List;

/**
 * History tab - completions grouped by day, newest first (Phase 6.2).
 *
 * <p><b>Read-only, and always from the rows.</b> Every figure here is derived from
 * {@code completion_history}: the days come from the stored {@code completionDateKey} and the counts come
 * from counting rows. Nothing is cached and nothing is stored, so this screen cannot disagree with the
 * Dashboard or with a completion detail - they are all reading the same table through Room's LiveData
 * (AD-4).
 *
 * <p><b>The grouping rule is not in this class.</b> It lives in {@code HistoryGrouper}, which is pure
 * Java and tested on the JVM (AD-11) - including the ordering, which is the part that would otherwise be
 * invisible if it went wrong. This Fragment observes, groups, and hands the result to the adapter.
 *
 * <p><b>A pushed screen opens from here, not a tab switch.</b> Tapping a day pushes Daily History on top,
 * so the back action returns to this list rather than to whichever tab happened to be selected before.
 */
public class HistoryFragment extends Fragment {

    private RecyclerView list;
    private LinearProgressIndicator loadingIndicator;
    private TextView emptyView;

    private HistoryAdapter adapter;

    /** The header card's round summary, and its hint line when there is no summary to give. */
    private TextView roundSummaryView;
    private TextView roundHintView;

    /** The latest history emission, kept so the round summary can be rebuilt when the other reads land. */
    private List<CompletionHistoryEntity> lastHistory = Collections.emptyList();

    private int currentRound = 1;
    private int totalStores;
    private boolean roundSourcesLoaded;

    public HistoryFragment() {
        super(R.layout.fragment_history);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        list = view.findViewById(R.id.history_list);
        loadingIndicator = view.findViewById(R.id.history_loading);
        emptyView = view.findViewById(R.id.tv_history_empty);

        adapter = new HistoryAdapter(this::openDay);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        // The way into the round report (Phase 12). Pushed rather than switched to: it is a step taken from
        // this tab, and the way out is back - where the user was.
        view.findViewById(R.id.card_history_rounds)
                .setOnClickListener(v -> openRoundReport());

        roundSummaryView = view.findViewById(R.id.tv_history_round_summary);
        roundHintView = view.findViewById(R.id.tv_history_round_hint);

        // The two numbers the round summary needs, neither of which changes while this tab is open often
        // enough to observe. One-shot reads, for the reason AD-37 records: a read must not depend on another
        // screen having observed the same LiveData first.
        AlphaRouteApp app = AlphaRouteApp.from(requireContext());
        app.settingsRepository().getNow(settings -> {
            if (settings != null) {
                currentRound = settings.currentRound;
            }
            app.storeRepository().findAllNow(stores -> {
                totalStores = countRealStores(stores);
                roundSourcesLoaded = true;
                // The history observer may have already fired and skipped the summary; this is the second
                // chance, and the summary cannot be built without both numbers.
                refreshRoundSummary();
            });
        });

        observeHistory();
    }

    /**
     * Fills in the round summary on the History tab's header card.
     *
     * <p>Silently does nothing until both reads have landed - the summary needs the round number and the
     * route size, and showing a half-built one would be worse than showing none.
     */
    private void refreshRoundSummary() {
        if (roundSummaryView == null || !roundSourcesLoaded) {
            return;
        }

        List<RoundReport> rounds = RoundGrouper.group(lastHistory, totalStores, currentRound);

        RoundReport current = null;
        for (RoundReport report : rounds) {
            if (report.roundNumber() == currentRound) {
                current = report;
                break;
            }
        }

        if (current == null || !current.hasWork()) {
            // Nothing done in this round yet, so there is no summary to give - the hint stands alone.
            roundSummaryView.setVisibility(View.GONE);
            return;
        }

        roundSummaryView.setVisibility(View.VISIBLE);
        roundSummaryView.setText(getString(
                R.string.rounds_summary_format,
                getResources().getQuantityString(
                        R.plurals.rounds_stores, current.totalStores(), current.totalStores()),
                getResources().getQuantityString(
                        R.plurals.rounds_days, current.daysTaken(), current.daysTaken())));
    }

    /** How many stores exist. 0 means "unknown" to the grouper, so nothing is marked complete. */
    private static int countRealStores(@Nullable List<StoreEntity> stores) {
        if (stores == null) {
            return 0;
        }
        int count = 0;
        for (StoreEntity store : stores) {
            if (store != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Opens the round report.
     *
     * <p>The title travels with the push as a resource id, not as a string, so it follows a locale change
     * and survives process death - the same rule every other pushed screen in the app follows (AD-26).
     */
    private void openRoundReport() {
        if (!isAdded() || !(requireActivity() instanceof MainActivity)) {
            return;
        }
        ((MainActivity) requireActivity()).push(
                RoundReportFragment.newInstance(),
                RoundReportFragment.TAG,
                R.string.title_round_report);
    }

    /**
     * Watches the whole completion history.
     *
     * <p>The same table the Dashboard reads, so a completion recorded anywhere appears here with no
     * wiring between the two screens - Room re-runs the query when the rows change.
     */
    private void observeHistory() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp.from(context)
                .completionRepository()
                .observeAllOrdered()
                .observe(getViewLifecycleOwner(), history -> {
                    if (loadingIndicator != null) {
                        loadingIndicator.setVisibility(View.GONE);
                    }

                    lastHistory = history == null ? Collections.emptyList() : history;

                    // Grouping happens here rather than in the adapter, so the adapter stays a rendering
                    // class and the rule stays testable on the JVM.
                    List<HistoryDay> days = HistoryGrouper.group(lastHistory);

                    adapter.submit(days);
                    emptyView.setVisibility(days.isEmpty() ? View.VISIBLE : View.GONE);

                    // The header card's summary comes from the same emission, so the two can never disagree
                    // about what has been done in this round.
                    refreshRoundSummary();
                });
    }

    /**
     * Opens one day's completions.
     *
     * <p>Pushed rather than shown in place, so the back action returns to this list. The date key travels
     * as an argument - the same rule every other screen in this app follows, because FragmentManager
     * recreates a fragment through its no-argument constructor after a process death and a field would be
     * lost.
     */
    private void openDay(@NonNull HistoryDay day) {
        if (!isAdded()) {
            return;
        }
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).push(
                    DailyHistoryFragment.newInstance(day.dateKey()),
                    DailyHistoryFragment.TAG,
                    R.string.title_daily_history);
        }
    }
}
