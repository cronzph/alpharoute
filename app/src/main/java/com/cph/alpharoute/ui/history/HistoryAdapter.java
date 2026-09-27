package com.cph.alpharoute.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.alpharoute.R;
import com.cph.alpharoute.domain.HistoryDay;
import com.cph.alpharoute.util.DateTimeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders the History screen's date groups.
 *
 * <p><b>The rows are already grouped and ordered</b> by {@code HistoryGrouper}, which is pure Java and
 * tested on the JVM. This class does no sorting and no counting - it only turns each {@link HistoryDay}
 * into one line of text. Keeping the rule out of the adapter is what makes the ordering testable without
 * a device.
 *
 * <p><b>The list is replaced wholesale, never patched.</b> {@code DiffUtil} would be cheaper, but the
 * history changes at most a few times a day and the list is short; the cost of a subtle diffing bug - a
 * row that shows a stale count - is higher than the cost of rebuilding.
 */
class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.DayViewHolder> {

    /** What a tap on a group does. Handled by the Fragment, so the adapter stays a rendering class. */
    interface OnDayClickListener {
        void onDayClick(@NonNull HistoryDay day);
    }

    private List<HistoryDay> days = Collections.emptyList();

    private final OnDayClickListener listener;

    HistoryAdapter(@NonNull OnDayClickListener listener) {
        this.listener = listener;
    }

    /** Replaces the contents. */
    void submit(@NonNull List<HistoryDay> newDays) {
        this.days = new ArrayList<>(newDays);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_date_group, parent, false);
        return new DayViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        holder.bind(days.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {

        private final TextView dayView;

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            dayView = itemView.findViewById(R.id.tv_history_day);
        }

        void bind(@NonNull HistoryDay day, @NonNull OnDayClickListener listener) {
            // The date is formatted FROM THE STORED KEY, not from a timestamp in the group - so the label
            // cannot disagree with the day the group actually represents. See DateTimeHelper.dateTextFromKey.
            String date = DateTimeHelper.dateTextFromKey(day.dateKey());
            String count = itemView.getResources().getQuantityString(
                    R.plurals.history_day_count, day.completedCount(), day.completedCount());

            dayView.setText(itemView.getContext()
                    .getString(R.string.history_day_format, date, count));

            itemView.setOnClickListener(v -> listener.onDayClick(day));
        }
    }
}
