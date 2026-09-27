package com.cph.alpharoute.ui.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.R;
import com.google.android.material.card.MaterialCardView;

/**
 * One progress card on the Dashboard: a small label above a large value.
 *
 * <p>Used six times in {@code fragment_dashboard.xml} (Completed today, Completed yesterday, Overall
 * completed, Remaining, Completion percentage, Latest completed). Phase 6.1 adds the seventh,
 * Total, when the Dashboard is connected to Room.
 *
 * <p><b>Why a custom view instead of six copies of the same XML:</b> the card is one concept used
 * seven times. A compound view keeps the markup in one file, and gives Phase 6.1 a typed API -
 * {@code cardToday.setValue("3")} - instead of a stringly-typed
 * {@code ((TextView) findViewById(R.id.tv_today_value)).setText(...)} chain per card.
 *
 * <p><b>Accessibility:</b> the two child TextViews are declared
 * {@code importantForAccessibility="no"} in {@code view_stat_card.xml}, and this class sets a single
 * {@code contentDescription} on the card. TalkBack therefore reads one sentence - "Completed today:
 * 3" - rather than two disconnected fragments. That description is refreshed automatically every
 * time the label or the value changes.
 */
public class StatCardView extends MaterialCardView {

    private TextView labelView;
    private TextView valueView;

    public StatCardView(@NonNull Context context) {
        this(context, null);
    }

    public StatCardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        // Mirrors MaterialCardView's own two-argument constructor: without this defStyleAttr the card
        // would not pick up the Material 3 card style from the theme and would render unstyled.
        this(context, attrs, com.google.android.material.R.attr.materialCardViewStyle);
    }

    public StatCardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_stat_card, this, true);

        labelView = findViewById(R.id.tv_stat_label);
        valueView = findViewById(R.id.tv_stat_value);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StatCardView);
            try {
                CharSequence label = a.getText(R.styleable.StatCardView_statLabel);
                CharSequence value = a.getText(R.styleable.StatCardView_statValue);
                if (label != null) {
                    labelView.setText(label);
                }
                if (value != null) {
                    valueView.setText(value);
                }
            } finally {
                // TypedArray is a shared, pooled object - it must always be recycled.
                a.recycle();
            }
        }

        refreshContentDescription();
    }

    /** Sets the small caption, e.g. "Completed today". */
    public void setLabel(@Nullable CharSequence label) {
        labelView.setText(label);
        refreshContentDescription();
    }

    /** Sets the large figure, e.g. "3" or "70.0%". */
    public void setValue(@Nullable CharSequence value) {
        valueView.setText(value);
        refreshContentDescription();
    }

    /** Keeps the TalkBack sentence in step with whatever is currently displayed. */
    private void refreshContentDescription() {
        setContentDescription(labelView.getText() + ": " + valueView.getText());
    }
}
