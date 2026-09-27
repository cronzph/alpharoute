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
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.AppSettingEntity;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.RoundGrouper;
import com.cph.alpharoute.domain.RoundReport;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The round report - how each round went (Phase 12).
 *
 * <p><b>What this screen answers that no other could.</b> The History tab shows one day; the Dashboard shows
 * the round in progress. Neither can say <i>"round 2 was 65 stores over 4 days - 23 on day 1, 18 on day
 * 2…"</i>, because that is a question about a whole round, and a round is not a day.
 *
 * <p><b>Read-only, like everything else in the History tab.</b> It reads and groups; it changes nothing. The
 * only interactive part is a day row, which expands to name the stores done that day.
 *
 * <p><b>One-shot reads rather than observed ones</b>, for the reason AD-37 records: this screen would
 * otherwise depend on another screen having observed the same repositories first, and would silently show
 * "no rounds" when restored straight to it. A report is a snapshot of a moment anyway.
 */
public class RoundReportFragment extends Fragment {

    /** Back-stack tag. */
    public static final String TAG = "RoundReportFragment";

    private RecyclerView list;
    private TextView emptyView;
    private LinearProgressIndicator loadingIndicator;

    private RoundReportAdapter adapter;

    /** Store id → code, so a day's expanded list can name its stores. */
    private final Map<Long, String> codesById = new HashMap<>();

    public RoundReportFragment() {
        super(R.layout.fragment_round_report);
    }

    /** The screen. It takes no arguments - everything it needs is in the database. */
    @NonNull
    public static RoundReportFragment newInstance() {
        return new RoundReportFragment();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        list = view.findViewById(R.id.rounds_list);
        emptyView = view.findViewById(R.id.tv_rounds_empty);
        loadingIndicator = view.findViewById(R.id.rounds_loading);

        adapter = new RoundReportAdapter(codesById);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        load();
    }

    /**
     * Reads the three things a report needs, then builds it.
     *
     * <p>Chained rather than parallel, so the report is built from one consistent set of rows - and so the
     * route size used to decide whether a round is complete is the one that goes with the history being
     * grouped.
     */
    private void load() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(context);

        app.settingsRepository().getNow(settings ->
                app.storeRepository().findAllNow(stores ->
                        app.completionRepository().findAllNow(history -> {
                            if (!isAdded()) {
                                return;
                            }
                            render(settings, stores, history);
                        })));
    }

    private void render(@Nullable AppSettingEntity settings,
                        @Nullable List<StoreEntity> stores,
                        @Nullable List<CompletionHistoryEntity> history) {

        loadingIndicator.setVisibility(View.GONE);

        int currentRound = settings == null ? 1 : settings.currentRound;
        int totalStores = countRealStores(stores);

        codesById.clear();
        if (stores != null) {
            for (StoreEntity store : stores) {
                if (store != null) {
                    codesById.put(store.id, store.storeCode);
                }
            }
        }

        List<RoundReport> rounds = RoundGrouper.group(history, totalStores, currentRound);

        if (rounds.isEmpty()) {
            // No round has any work in it. Saying so beats an empty screen with no explanation.
            list.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            adapter.submit(Collections.emptyList());
            return;
        }

        emptyView.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        adapter.submit(rounds);
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
}
