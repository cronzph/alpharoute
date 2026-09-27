package com.cph.alpharoute.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.alpharoute.R;
import com.cph.alpharoute.util.DateTimeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renders one day's completions.
 *
 * <p><b>The rows arrive already in the right order</b> - the DAO sorts by {@code dailySequence ASC}, which
 * is the real order of completion within the day, not route-number order. This adapter does not sort, so
 * the ordering rule lives in exactly one place and cannot be applied differently here.
 *
 * <p>{@link Row} is a join of a completion with its store, built by the Fragment. It is a UI row model
 * rather than a domain type: nothing in it is a rule, it is simply what the three lines of text need.
 */
class DailyHistoryAdapter extends RecyclerView.Adapter<DailyHistoryAdapter.RowViewHolder> {

    /** What a tap on a row does. Handled by the Fragment. */
    interface OnRowClickListener {
        void onRowClick(@NonNull Row row);
    }

    /**
     * One joined row: a completion and the store it belongs to.
     *
     * <p>{@code storeId} and {@code historyId} are carried so the detail screen can be opened by id rather
     * than by a snapshot - the same rule the rest of the app follows, because a snapshot can be stale by
     * the time the next screen reads it (AD-4).
     */
    static class Row {
        final long historyId;
        final long storeId;
        final int dailySequence;
        final String routeNumber;
        final String storeCode;
        final String storeName;
        final String area;
        final long completionTimestamp;

        Row(long historyId,
            long storeId,
            int dailySequence,
            @Nullable String routeNumber,
            @Nullable String storeCode,
            @Nullable String storeName,
            @Nullable String area,
            long completionTimestamp) {
            this.historyId = historyId;
            this.storeId = storeId;
            this.dailySequence = dailySequence;
            this.routeNumber = routeNumber == null ? "" : routeNumber;
            this.storeCode = storeCode == null ? "" : storeCode;
            this.storeName = storeName == null ? "" : storeName;
            this.area = area == null ? "" : area;
            this.completionTimestamp = completionTimestamp;
        }
    }

    private List<Row> rows = Collections.emptyList();

    private final OnRowClickListener listener;

    DailyHistoryAdapter(@NonNull OnRowClickListener listener) {
        this.listener = listener;
    }

    void submit(@NonNull List<Row> newRows) {
        this.rows = new ArrayList<>(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_row, parent, false);
        return new RowViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        holder.bind(rows.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class RowViewHolder extends RecyclerView.ViewHolder {

        private final TextView identifiersView;
        private final TextView nameView;
        private final TextView areaTimeView;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            identifiersView = itemView.findViewById(R.id.tv_row_identifiers);
            nameView = itemView.findViewById(R.id.tv_row_name);
            areaTimeView = itemView.findViewById(R.id.tv_row_area_time);
        }

        void bind(@NonNull Row row, @NonNull OnRowClickListener listener) {
            // "#1" with a hash, because the sequence is a position within the day rather than an id - and
            // a bare "1" beside a store code would read as another code.
            identifiersView.setText(itemView.getContext().getString(
                    R.string.daily_row_identifiers_format,
                    row.dailySequence,
                    row.routeNumber,
                    row.storeCode));

            nameView.setText(row.storeName);

            areaTimeView.setText(itemView.getContext().getString(
                    R.string.daily_row_area_time_format,
                    row.area,
                    DateTimeHelper.timeText(row.completionTimestamp)));

            itemView.setOnClickListener(v -> listener.onRowClick(row));
        }
    }
}
