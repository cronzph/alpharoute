package com.cph.alpharoute.ui.route;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.cph.alpharoute.R;
import com.cph.alpharoute.domain.OptimizationStop;
import com.cph.alpharoute.util.RouteTextFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Renders the ordered stops of a route.
 *
 * <p><b>The rows arrive in the order to drive them</b> and are rendered in it. Nothing here sorts, so the
 * order the user sees is exactly the order the plan holds - and the only thing this class decides is how
 * to phrase it.
 *
 * <p><b>The number comes from the row's position, never from the data.</b> That is the one rule worth
 * stating: a stop's number is where it sits in the list, so when a row is dragged the numbers are right
 * immediately, without anything having to renumber a model. A sequence carried in the data would have to be
 * rewritten on every move, and a list showing "#1, #2, #2" after a drag is worse than one showing nothing.
 *
 * <p><b>Each stop is joined with its store by the Fragment</b>, because the plan carries store ids and the
 * screen needs names. {@link Row} is that join: a UI model, not a rule.
 */
class OptimizedRouteAdapter extends RecyclerView.Adapter<OptimizedRouteAdapter.StopViewHolder> {

    /** What a tap on a stop does. */
    interface OnStopClickListener {
        void onStopClick(@NonNull Row row);
    }

    /** What pressing the grip does. The list's drag helper decides what happens next. */
    interface OnStartDragListener {
        void onStartDrag(@NonNull RecyclerView.ViewHolder holder);
    }

    /** One stop, joined with the store it refers to. */
    static class Row {
        final long storeId;
        final long arrivalSeconds;
        final double legDistanceMetres;
        final long legDurationSeconds;
        @Nullable
        final String routeNumber;
        @Nullable
        final String storeCode;
        @Nullable
        final String storeName;

        Row(@NonNull OptimizationStop stop,
            @Nullable String routeNumber,
            @Nullable String storeCode,
            @Nullable String storeName) {
            this.storeId = stop.storeId();
            this.arrivalSeconds = stop.arrivalSeconds();
            this.legDistanceMetres = stop.legDistanceMetres();
            this.legDurationSeconds = stop.legDurationSeconds();
            this.routeNumber = routeNumber;
            this.storeCode = storeCode;
            this.storeName = storeName;
        }

        /** Whether the store behind this stop could be found. */
        boolean hasStore() {
            return storeCode != null || storeName != null;
        }
    }

    private List<Row> rows = Collections.emptyList();

    private final OnStopClickListener clickListener;
    private final OnStartDragListener dragListener;

    /**
     * Whether the user is moving a row right now.
     *
     * <p>While true, every leg distance is hidden. A leg describes the step from the stop before it, so the
     * moment a row is picked up the number under it describes a journey that is no longer on screen - and
     * showing a stale figure that looks current is exactly the kind of quiet wrong answer this project
     * avoids. The figures come back, recalculated, the instant the row is dropped.
     */
    private boolean reordering;

    OptimizedRouteAdapter(@NonNull OnStopClickListener clickListener,
                          @NonNull OnStartDragListener dragListener) {
        this.clickListener = clickListener;
        this.dragListener = dragListener;
    }

    void submit(@NonNull List<Row> newRows) {
        this.rows = new ArrayList<>(newRows);
        notifyDataSetChanged();
    }

    /**
     * Moves a row, and the data under it, to a new position.
     *
     * <p>Called from the drag helper while a row is being dragged. The move is stepped one place at a time
     * so that {@code notifyItemMoved} describes a swap of neighbours, which is what keeps the animation
     * smooth rather than making rows jump.
     *
     * <p>The Fragment swaps its own copy of the order the same way, and re-submits the whole list when the
     * drag ends - so the two cannot drift apart even if a move is somehow missed.
     */
    void move(int from, int to) {
        if (from < to) {
            for (int i = from; i < to; i++) {
                Collections.swap(rows, i, i + 1);
            }
        } else {
            for (int i = from; i > to; i--) {
                Collections.swap(rows, i, i - 1);
            }
        }
        notifyItemMoved(from, to);
    }

    /** Whether a row is being dragged. */
    boolean isReordering() {
        return reordering;
    }

    void setReordering(boolean reordering) {
        this.reordering = reordering;
    }

    @NonNull
    @Override
    public StopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route_stop, parent, false);
        return new StopViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull StopViewHolder holder, int position) {
        holder.bind(rows.get(position), position, reordering, clickListener, dragListener);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class StopViewHolder extends RecyclerView.ViewHolder {

        private final TextView sequenceView;
        private final TextView identifiersView;
        private final TextView nameView;
        private final ImageView dragHandle;

        StopViewHolder(@NonNull View itemView) {
            super(itemView);
            sequenceView = itemView.findViewById(R.id.tv_stop_sequence);
            identifiersView = itemView.findViewById(R.id.tv_stop_identifiers);
            nameView = itemView.findViewById(R.id.tv_stop_name);
            dragHandle = itemView.findViewById(R.id.iv_stop_drag_handle);
        }

        @SuppressLint("ClickableViewAccessibility")
        void bind(@NonNull Row row,
                  int position,
                  boolean reordering,
                  @NonNull OnStopClickListener clickListener,
                  @NonNull OnStartDragListener dragListener) {

            // The number is the position. See the class note - this is what makes a drag renumber for free.
            String leg = reordering ? "" : legText(row);
            sequenceView.setText(leg.isEmpty()
                    ? itemView.getContext().getString(R.string.route_stop_sequence_only, position + 1)
                    : itemView.getContext().getString(
                            R.string.route_stop_sequence_format, position + 1, leg));

            if (row.hasStore()) {
                identifiersView.setText(itemView.getContext().getString(
                        R.string.completion_store_format,
                        row.routeNumber == null ? "" : row.routeNumber,
                        row.storeCode == null ? "" : row.storeCode));
                nameView.setText(row.storeName == null ? "" : row.storeName);
            } else {
                // The plan names a store the app does not have. AD-6 makes that impossible, so if it
                // happens the record is inconsistent - say so rather than showing a blank row.
                identifiersView.setText(itemView.getContext()
                        .getString(R.string.route_stop_missing_store, row.storeId));
                nameView.setText("");
            }

            itemView.setOnClickListener(v -> clickListener.onStopClick(row));

            // The grip. ACTION_DOWN only, so the drag starts on the press rather than after the finger has
            // already moved - waiting for a move would fight the RecyclerView's own scroll gesture.
            dragHandle.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragListener.onStartDrag(this);
                }
                return false;
            });
        }

        /**
         * The leg into this stop, or an empty string when there is nothing to say.
         *
         * <p><b>The distance is shown on its own when the time is not known</b>, which is every route
         * planned on the device. The two parts used to be omitted together, on the reasoning that a
         * distance with no duration looks like a missing value rather than an unknown one - but the
         * distance is real and is the only number the local planner can honestly give, so dropping it
         * along with the time hid the useful half.
         *
         * <p>A leg with no distance is still shown as nothing at all: "0 m" would be a claim that the user
         * is already standing in the shop.
         */
        @NonNull
        private String legText(@NonNull Row row) {
            if (row.legDistanceMetres <= 0.0) {
                return "";
            }

            String distance = RouteTextFormatter.distanceText((int) Math.round(row.legDistanceMetres));
            if (row.legDurationSeconds <= 0L) {
                return distance;
            }

            return String.format(
                    Locale.US,
                    "%s · %s",
                    distance,
                    // Clamped before the cast: a route of a few hours is a few tens of thousands of
                    // seconds, nowhere near the limit, but an unclamped cast of a corrupt value would go
                    // negative and render as a nonsense duration.
                    RouteTextFormatter.durationText(
                            (int) Math.min(Integer.MAX_VALUE, row.legDurationSeconds)));
        }
    }
}
