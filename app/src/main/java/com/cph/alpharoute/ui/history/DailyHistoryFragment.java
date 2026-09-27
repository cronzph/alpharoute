package com.cph.alpharoute.ui.history;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.MainActivity;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.util.DateTimeHelper;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily History - one day's completions, in the order they happened (Phase 6.3).
 *
 * <p><b>The order is not decided here.</b> The DAO's query sorts by {@code dailySequence ASC}, which is
 * the real order of completion within the day - not route-number order, and not the order the rows happen
 * to sit in the table. The screen plan asks for exactly that, and putting the sort in SQL is what makes it
 * the single definition.
 *
 * <p><b>Two sources, joined.</b> The completion rows carry a {@code storeId} but not the store's name or
 * area, so the day's rows and the store list are combined here. A {@code MediatorLiveData} does the
 * joining, so a store renamed while this screen is open updates the row it appears on - and the rows are
 * rebuilt from both lists rather than patched, so a stale name cannot survive.
 *
 * <p><b>The date key travels as an argument.</b> FragmentManager recreates this fragment through its
 * no-argument constructor after a process death, so a field would be lost and the screen would open on
 * nothing.
 */
public class DailyHistoryFragment extends Fragment {

    /** Back-stack tag. */
    public static final String TAG = "DailyHistoryFragment";

    private static final String ARG_DATE_KEY = "arg_date_key";

    private RecyclerView list;
    private TextView headerView;
    private TextView emptyView;
    private LinearProgressIndicator loadingIndicator;

    private DailyHistoryAdapter adapter;
    private String dateKey = "";

    public DailyHistoryFragment() {
        super(R.layout.fragment_daily_history);
    }

    /** The screen for one local day, identified by its stored {@code YYYY-MM-DD} key. */
    @NonNull
    public static DailyHistoryFragment newInstance(@NonNull String dateKey) {
        DailyHistoryFragment fragment = new DailyHistoryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_DATE_KEY, dateKey);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            String fromArgs = args.getString(ARG_DATE_KEY);
            dateKey = fromArgs == null ? "" : fromArgs;
        }

        list = view.findViewById(R.id.daily_list);
        headerView = view.findViewById(R.id.tv_daily_header);
        emptyView = view.findViewById(R.id.tv_daily_empty);
        loadingIndicator = view.findViewById(R.id.daily_loading);

        adapter = new DailyHistoryAdapter(this::openCompletionDetail);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        observeDay();
    }

    /**
     * Joins this day's completions with the store list.
     *
     * <p>A {@code MediatorLiveData} rather than two independent observers, for the same reason the
     * Dashboard uses one: the rows need both inputs, and updating on each source separately would render
     * twice per change - once with a list that has no names in it.
     */
    private void observeDay() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        AlphaRouteApp app = AlphaRouteApp.from(context);

        // Cached per emission. Both are replaced wholesale, so neither can drift from what Room last said.
        final List<StoreEntity>[] stores = new List[]{Collections.emptyList()};

        MediatorLiveData<List<DailyHistoryAdapter.Row>> joined = new MediatorLiveData<>();

        LiveData<List<CompletionHistoryEntity>> daySource =
                app.completionRepository().observeByDateKey(dateKey);
        LiveData<List<StoreEntity>> storeSource = app.storeRepository().observeAll();

        joined.addSource(daySource, completions -> {
            joined.setValue(join(completions, stores[0]));
        });

        joined.addSource(storeSource, emitted -> {
            stores[0] = emitted == null ? Collections.emptyList() : emitted;
            // Re-joined against the latest day list, so a store renamed mid-view updates its row.
            joined.setValue(join(daySource.getValue(), stores[0]));
        });

        joined.observe(getViewLifecycleOwner(), rows -> {
            if (loadingIndicator != null) {
                loadingIndicator.setVisibility(View.GONE);
            }

            List<DailyHistoryAdapter.Row> safeRows = rows == null ? Collections.emptyList() : rows;
            adapter.submit(safeRows);
            emptyView.setVisibility(safeRows.isEmpty() ? View.VISIBLE : View.GONE);
            renderHeader(safeRows.size());
        });
    }

    /** The header: the day, and how many stores were completed on it. */
    private void renderHeader(int count) {
        if (headerView == null) {
            return;
        }
        String date = DateTimeHelper.dateTextFromKey(dateKey);
        String countText = getResources().getQuantityString(R.plurals.history_day_count, count, count);
        headerView.setText(getString(R.string.history_day_format, date, countText));
    }

    /**
     * Builds the rows by matching each completion to its store.
     *
     * <p>The store map is built once per join rather than searching the list per row, so this stays a
     * single pass. A completion whose store is missing still produces a row - with blank identifiers -
     * rather than being dropped: silently hiding a record would make the count here disagree with the
     * History screen, and Phase 6.5's consistency check is what reports the real problem.
     */
    @NonNull
    private List<DailyHistoryAdapter.Row> join(@Nullable List<CompletionHistoryEntity> completions,
                                               @Nullable List<StoreEntity> stores) {
        if (completions == null || completions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, StoreEntity> byId = new HashMap<>();
        if (stores != null) {
            for (StoreEntity store : stores) {
                if (store != null) {
                    byId.put(store.id, store);
                }
            }
        }

        List<DailyHistoryAdapter.Row> rows = new ArrayList<>(completions.size());
        for (CompletionHistoryEntity completion : completions) {
            if (completion == null) {
                continue;
            }
            StoreEntity store = byId.get(completion.storeId);

            rows.add(new DailyHistoryAdapter.Row(
                    completion.id,
                    completion.storeId,
                    completion.dailySequence,
                    store == null ? null : store.routeNumber,
                    store == null ? null : store.storeCode,
                    store == null ? null : store.storeName,
                    store == null ? null : store.area,
                    completion.completionTimestamp));
        }
        return rows;
    }

    /**
     * Opens the full record for one completion.
     *
     * <p>Pushed on top, so back returns to this day. <b>The history row id travels, not the store id</b> -
     * since rounds arrived a store has one completion per round, so the row is the only thing that names
     * exactly which record this is and which one an undo would remove.
     */
    private void openCompletionDetail(@NonNull DailyHistoryAdapter.Row row) {
        if (!isAdded()) {
            return;
        }
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).push(
                    CompletionDetailFragment.newInstance(row.historyId),
                    CompletionDetailFragment.TAG,
                    R.string.title_completion_detail);
        }
    }
}
