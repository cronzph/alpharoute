package com.cph.alpharoute.ui.store;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.repository.PlaceLinkRepository;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.PlaceLinkState;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Add a store from a Google Maps link.
 *
 * <p><b>Why this exists.</b> Google Maps already knows where every Alfamart is. Pinning one by eye on our
 * own map, from memory of where the shop is, is guesswork - and with a 50 m arrival radius, a pin a few
 * hundred metres out means the app never offers to complete that store, and never explains why. Copying
 * the location out of Google Maps removes the guesswork entirely.
 *
 * <p><b>It hands off rather than duplicating.</b> Once a location has been read, this sheet closes and
 * opens the ordinary Add Store form with the coordinates filled in. That form needs no change at all - it
 * already takes a latitude and a longitude, and it has no business knowing whether they came from a
 * long-press on the map or from a pasted link.
 *
 * <p><b>The clipboard is read on open.</b> The user has just tapped Copy in Google Maps, so the link is
 * almost always already there and the common case becomes one tap on Continue rather than a long-press,
 * a Paste, and a confirm. If the clipboard holds nothing useful the field is simply left empty - reading
 * it is a convenience, never a requirement.
 */
public class AddFromLinkBottomSheet extends BottomSheetDialogFragment {

    /** Dialog tag, so the map can tell whether it is already showing. */
    public static final String TAG = "AddFromLinkBottomSheet";

    private TextInputLayout linkLayout;
    private TextInputEditText linkInput;
    private LinearProgressIndicator progress;
    private TextView resultView;
    private MaterialButton continueButton;

    private PlaceLinkRepository placeLinkRepository;

    /** The location read from the link, or {@code null} until one has been. */
    @Nullable
    private GeoCoordinate resolved;

    public AddFromLinkBottomSheet() {
        super(R.layout.sheet_add_from_link);
    }

    public static AddFromLinkBottomSheet newInstance() {
        return new AddFromLinkBottomSheet();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        linkLayout = view.findViewById(R.id.til_place_link);
        linkInput = view.findViewById(R.id.et_place_link);
        progress = view.findViewById(R.id.place_link_progress);
        resultView = view.findViewById(R.id.tv_place_link_result);
        continueButton = view.findViewById(R.id.btn_place_link_continue);

        placeLinkRepository = AlphaRouteApp.from(requireContext()).placeLinkRepository();

        MaterialButton pasteButton = view.findViewById(R.id.btn_place_link_paste);
        pasteButton.setOnClickListener(v -> pasteFromClipboard());

        MaterialButton cancelButton = view.findViewById(R.id.btn_place_link_cancel);
        cancelButton.setOnClickListener(v -> dismiss());

        continueButton.setOnClickListener(v -> onContinueClicked());

        observeState();

        // Only on a fresh view. After a rotation the field and the result are already restored, and
        // reading the clipboard again would overwrite whatever the user had typed.
        if (savedInstanceState == null) {
            pasteFromClipboard();
        }
    }

    // ------------------------------------------------------------------ the state

    private void observeState() {
        placeLinkRepository.observe().observe(getViewLifecycleOwner(), this::render);
    }

    /** Brings the whole sheet in line with one state. One place, so no two parts can disagree. */
    private void render(@Nullable PlaceLinkState state) {
        if (state == null) {
            return;
        }

        resolved = state.isFound() ? state.coordinate() : null;

        progress.setVisibility(state.isResolving() ? View.VISIBLE : View.GONE);

        // Continue needs a real coordinate: the entire point of this sheet is to hand one to the Add Store
        // form, and continuing without one would open a store with nowhere to be.
        continueButton.setEnabled(resolved != null);

        if (resolved != null) {
            linkLayout.setError(null);
            resultView.setText(getString(
                    R.string.place_link_found_format,
                    getString(R.string.coordinates_format,
                            resolved.latitude(), resolved.longitude())));
            return;
        }

        if (state.status() == PlaceLinkState.Status.IDLE) {
            resultView.setText("");
            return;
        }

        resultView.setText(messageFor(state.status()));
    }

    @StringRes
    private static int messageFor(@NonNull PlaceLinkState.Status status) {
        switch (status) {
            case RESOLVING:
                return R.string.place_link_resolving;
            case NOT_A_LINK:
                return R.string.place_link_not_a_link;
            case UNRESOLVED:
                return R.string.place_link_unresolved;
            case OFFLINE:
                return R.string.place_link_offline;
            case FAILED:
            case IDLE:
            case FOUND:
            default:
                // IDLE and FOUND never reach here - both are handled by render() before this is called.
                return R.string.place_link_failed;
        }
    }

    // ------------------------------------------------------------------ the clipboard

    private void pasteFromClipboard() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        String value = readClipboard(context);
        if (value.isEmpty()) {
            resultView.setText(R.string.place_link_nothing_pasted);
            return;
        }

        linkInput.setText(value);
        linkInput.setSelection(value.length());
        placeLinkRepository.resolve(value);
    }

    /**
     * The clipboard's text, or an empty string.
     *
     * <p>{@code getText()} first, because a plain copied link is the ordinary case and it is free.
     * {@code coerceToText} is the fallback for a clip that holds something else - a content URI, say - and
     * it is only reached when it is needed, because it can read from another app.
     */
    @NonNull
    private static String readClipboard(@NonNull Context context) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return "";
        }

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return "";
        }

        ClipData.Item item = clip.getItemAt(0);
        CharSequence text = item.getText();
        if (text == null) {
            text = item.coerceToText(context);
        }
        return text == null ? "" : text.toString().trim();
    }

    // ------------------------------------------------------------------ handing off

    private void onContinueClicked() {
        if (resolved == null) {
            return;
        }

        FragmentManager fragmentManager = getParentFragmentManager();
        final double latitude = resolved.latitude();
        final double longitude = resolved.longitude();

        dismiss();

        // The ordinary Add Store form, with the coordinates already filled in. It does not know where they
        // came from, and it does not need to.
        AddEditStoreBottomSheet.newInstance(latitude, longitude)
                .show(fragmentManager, AddEditStoreBottomSheet.TAG);
    }

    @Override
    public void onDestroy() {
        // Drop the result when this sheet is genuinely going away, so a later visit does not open showing
        // a location read minutes ago. A rotation also destroys the fragment, and clearing there would
        // throw away a good result and make the user paste again - so isRemoving() is what tells the two
        // apart, exactly as the Route Preview sheet does.
        if (placeLinkRepository != null) {
            boolean activityGone = getActivity() == null || getActivity().isFinishing();
            if (isRemoving() || activityGone) {
                placeLinkRepository.clear();
            }
        }
        super.onDestroy();
    }
}
