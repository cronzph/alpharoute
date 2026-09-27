package com.cph.alpharoute.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.alpharoute.R;
import com.cph.alpharoute.domain.RoundDay;
import com.cph.alpharoute.domain.RoundReport;
import com.cph.alpharoute.util.DateTimeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders the round report: one card per round, with a row per day inside it.
 *
 * <p><b>The day rows are built in code, not by a nested RecyclerView.</b> A round has a handful of days, the
 * whole card is one scroll item, and a scrolling list inside a scrolling list is a known source of
 * measurement bugs for no benefit at this size.
 *
 * <p><b>The expanded state lives here, not in the views.</b> A RecyclerView recycles its rows, so a view
 * that remembered whether it was open would come back open on a different round. The adapter holds the set
 * of expanded {@code round#day} keys and rebuilds the rows to match on every bind, which is what makes the
 * state survive scrolling.
 */
class RoundReportAdapter extends RecyclerView.Adapter<RoundReportAdapter.RoundViewHolder> {

    private List<RoundReport> rounds = Collections.emptyList();

    /**
     * Which day rows are open, keyed {@code roundNumber#dayNumber}.
     *
     * <p>A set of keys rather than a set of positions: positions shift when the list changes, and an
     * expanded row would jump to a different round.
     */
    private final Set<String> expanded = new HashSet<>();

    /** Store id → the code to show, so a day's list can name its stores. */
    final Map<Long, String> codesById;

    RoundReportAdapter(@NonNull Map<Long, String> codesById) {
        this.codesById = codesById;
    }

    void submit(@NonNull List<RoundReport> newRounds) {
        this.rounds = new ArrayList<>(newRounds);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RoundViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_round_report, parent, false);
        return new RoundViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull RoundViewHolder holder, int position) {
        holder.bind(rounds.get(position), this);
    }

    @Override
    public int getItemCount() {
        return rounds.size();
    }

    private boolean isExpanded(int roundNumber, int dayNumber) {
        return expanded.contains(roundNumber + "#" + dayNumber);
    }

    private void toggle(int roundNumber, int dayNumber) {
        String key = roundNumber + "#" + dayNumber;
        if (!expanded.remove(key)) {
            expanded.add(key);
        }
    }

    static class RoundViewHolder extends RecyclerView.ViewHolder {

        private final TextView titleView;
        private final TextView summaryView;
        private final TextView rangeView;
        private final LinearLayout daysGroup;

        RoundViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.tv_round_title);
            summaryView = itemView.findViewById(R.id.tv_round_summary);
            rangeView = itemView.findViewById(R.id.tv_round_range);
            daysGroup = itemView.findViewById(R.id.group_round_days);
        }

        void bind(@NonNull RoundReport round, @NonNull RoundReportAdapter adapter) {
            titleView.setText(itemView.getContext()
                    .getString(R.string.rounds_title_format, round.roundNumber()));

            // "65 stores · 4 days" - the headline. Both pluralised, because "1 stores" is the kind of
            // detail that makes an app feel unfinished.
            summaryView.setText(itemView.getContext().getString(
                    R.string.rounds_summary_format,
                    itemView.getContext().getResources().getQuantityString(
                            R.plurals.rounds_stores, round.totalStores(), round.totalStores()),
                    itemView.getContext().getResources().getQuantityString(
                            R.plurals.rounds_days, round.daysTaken(), round.daysTaken())));

            rangeView.setText(rangeText(round));

            // Rebuilt on every bind rather than reused. The rows are cheap - a handful per round - and
            // reusing them would mean tracking which child is which across a rebind, which is exactly the
            // bookkeeping that goes wrong when a RecyclerView recycles.
            daysGroup.removeAllViews();
            for (RoundDay day : round.days()) {
                daysGroup.addView(buildDayRow(round.roundNumber(), day, adapter));
            }
        }

        /**
         * The range line: {@code 1 Sep 2026 – 4 Sep 2026}.
         *
         * <p>Collapses to a single date when the round was finished in one day - "1 Sep 2026 – 1 Sep 2026"
         * is a range of one, and reading it as a range invites the question of what happened in between.
         */
        @NonNull
        private String rangeText(@NonNull RoundReport round) {
            String start = DateTimeHelper.dateTextFromKey(round.startDateKey());
            String end = DateTimeHelper.dateTextFromKey(round.endDateKey());

            if (start.isEmpty() && end.isEmpty()) {
                return "";
            }
            if (start.equals(end)) {
                return start;
            }
            return itemView.getContext().getString(R.string.rounds_range_format, start, end);
        }

        /** One day row, with its store list hidden until tapped. */
        @NonNull
        private View buildDayRow(int roundNumber, @NonNull RoundDay day,
                                 @NonNull RoundReportAdapter adapter) {
            View row = LayoutInflater.from(itemView.getContext())
                    .inflate(R.layout.item_round_day, daysGroup, false);

            TextView titleView = row.findViewById(R.id.tv_day_title);
            TextView storesView = row.findViewById(R.id.tv_day_stores);

            titleView.setText(itemView.getContext().getString(
                    R.string.rounds_day_format,
                    day.dayNumber(),
                    DateTimeHelper.shortDateTextFromKey(day.dateKey()),
                    itemView.getContext().getResources().getQuantityString(
                            R.plurals.rounds_stores, day.count(), day.count())));

            boolean open = adapter.isExpanded(roundNumber, day.dayNumber());
            storesView.setText(storeList(day, adapter.codesById));
            storesView.setVisibility(open ? View.VISIBLE : View.GONE);

            row.setOnClickListener(v -> {
                adapter.toggle(roundNumber, day.dayNumber());
                // Toggled in place rather than by notifying the adapter: the card's own content has not
                // changed, so a full rebind would rebuild every day row on this round to flip one line.
                boolean nowOpen = adapter.isExpanded(roundNumber, day.dayNumber());
                storesView.setVisibility(nowOpen ? View.VISIBLE : View.GONE);
            });

            return row;
        }

        /**
         * The codes of the stores done that day, comma-separated.
         *
         * <p>A store id with no matching row shows as {@code #id} rather than being dropped: the count in the
         * title already includes it, so silently omitting it would make the list disagree with its own
         * heading.
         */
        @NonNull
        private String storeList(@NonNull RoundDay day, @NonNull Map<Long, String> codesById) {
            StringBuilder text = new StringBuilder();
            for (Long storeId : day.storeIds()) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                String code = storeId == null ? null : codesById.get(storeId);
                text.append(code != null
                        ? code
                        : itemView.getContext().getString(R.string.rounds_unknown_store, storeId));
            }
            return text.toString();
        }
    }
}
