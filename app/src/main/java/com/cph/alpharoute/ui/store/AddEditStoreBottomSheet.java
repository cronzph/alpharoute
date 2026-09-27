package com.cph.alpharoute.ui.store;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.cph.alpharoute.AlphaRouteApp;
import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.data.repository.StoreRepository;
import com.cph.alpharoute.data.repository.WriteOutcome;
import com.cph.alpharoute.domain.StoreFieldError;
import com.cph.alpharoute.domain.StoreValidationResult;
import com.cph.alpharoute.domain.StoreValidator;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * The Add / Edit Store form.
 *
 * <p>Two modes, one screen. Phase 3.3 built it for <b>adding</b>, opened by long-pressing the map.
 * Phase 3.6 adds <b>editing</b>, opened from the Edit action on the Store Details sheet. It is the same
 * form because it is the same four fields with the same rules - a second, nearly identical screen would
 * be two places for the validation to drift apart.
 *
 * <p><b>The coordinates are never typed, in either mode.</b> In add mode they come from the point the
 * user long-pressed; in edit mode they are the store's own, shown read-only. Letting them be typed would
 * invite a store to be pinned somewhere the user never actually chose.
 *
 * <p><b>Edit mode re-reads the store rather than being handed its values.</b> That is the same rule the
 * Store Details sheet follows (assumption A70): Room is the single source of truth, so the form shows
 * what is actually stored, and a store deleted a moment ago is reported immediately rather than after
 * the user has filled in the whole form. The id travels in the arguments, so a rotation simply re-reads.
 *
 * <p><b>Rotation must not overwrite what the user has typed.</b> The framework restores the contents of
 * every text field itself, so the form fills them in only when there is no saved state to restore. The
 * store is still re-read either way, because the save needs the row to exist.
 *
 * <p><b>Validation happens in two stages, and the order matters.</b> {@link StoreValidator} runs first,
 * synchronously, so a blank field is reported the instant Save is tapped with no spinner and no database
 * round trip. Only if that passes does the repository run, which is where the duplicate checks live
 * because they need the database and the database is asynchronous. See §13.3 of the Phase 2.2 document.
 *
 * <p><b>An edit cannot change a store's status.</b> The entity this form builds carries only the fields
 * it owns; {@link StoreRepository} copies the status, the creation time and the completion time back
 * from the stored row before writing. That is enforced in the repository rather than here, so it holds
 * however this form is built - see {@code StoreRepository.preserveUneditableFields}.
 *
 * <p><b>The rapid-tap guard is about experience, not correctness.</b> A double tap cannot create two
 * stores - the unique index on {@code storeCode} makes that impossible, and the repository's pre-check
 * plus the single-threaded disk executor make it doubly so. Disabling the buttons simply avoids the user
 * seeing a pointless second attempt.
 */
public class AddEditStoreBottomSheet extends BottomSheetDialogFragment {

    /**
     * Dialog tag, so the map can check whether the form is already showing.
     *
     * <p><b>No fragment result is published.</b> Phase 3.3 handed the new store's id back to the map,
     * which then drew one marker. Phase 3.4 replaced that: the map now observes the whole store table,
     * so the marker appears the moment Room reports the insert - and, unlike the old path, it also
     * survives a restart and covers deletions. The result plumbing was removed rather than left in
     * place unused.
     *
     * <p>The same reasoning covers an edit: the map re-renders from Room, so a renamed store's marker
     * needs nobody to tell it anything.
     */
    public static final String TAG = "AddEditStoreBottomSheet";

    private static final String ARG_LATITUDE = "arg_latitude";
    private static final String ARG_LONGITUDE = "arg_longitude";
    private static final String ARG_STORE_ID = "arg_store_id";

    /** A store id that cannot exist - Room's autoGenerate primary key starts at 1. Means "add mode". */
    private static final long NO_STORE_ID = -1L;

    private TextInputLayout routeNumberLayout;
    private TextInputLayout storeCodeLayout;
    private TextInputLayout storeNameLayout;
    private TextInputLayout areaLayout;

    private TextInputEditText routeNumberInput;
    private TextInputEditText storeCodeInput;
    private TextInputEditText storeNameInput;
    private TextInputEditText areaInput;

    private TextView coordinatesView;

    private MaterialButton saveButton;
    private MaterialButton cancelButton;
    private LinearProgressIndicator progress;

    private double latitude;
    private double longitude;

    /** The store being edited, or {@link #NO_STORE_ID} when adding. */
    private long storeId = NO_STORE_ID;

    /** True while a save is in flight. The first thing every Save tap checks. */
    private boolean saving;

    /** True while an existing store is being read. Edit mode only. */
    private boolean loading;

    /**
     * Whether to fill the fields in once the store arrives.
     *
     * <p>False after a rotation, because the framework has already restored whatever the user typed and
     * filling the fields in again would throw their unsaved changes away.
     */
    private boolean prefillWhenLoaded;

    public AddEditStoreBottomSheet() {
        super(R.layout.sheet_add_edit_store);
    }

    /**
     * Creates the form for a new store at a map point.
     *
     * <p>Values go through arguments rather than a constructor parameter, because FragmentManager
     * recreates fragments through the no-argument constructor and a value held only in a field would be
     * lost on rotation.
     */
    public static AddEditStoreBottomSheet newInstance(double latitude, double longitude) {
        AddEditStoreBottomSheet sheet = new AddEditStoreBottomSheet();
        Bundle args = new Bundle();
        args.putDouble(ARG_LATITUDE, latitude);
        args.putDouble(ARG_LONGITUDE, longitude);
        args.putLong(ARG_STORE_ID, NO_STORE_ID);
        sheet.setArguments(args);
        return sheet;
    }

    /**
     * Creates the form for an existing store.
     *
     * <p>Only the id is passed. The fields are filled in from the database once the sheet is shown, so
     * the form can never display a value the store does not have.
     */
    public static AddEditStoreBottomSheet newInstanceForEdit(long storeId) {
        AddEditStoreBottomSheet sheet = new AddEditStoreBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_STORE_ID, storeId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            latitude = args.getDouble(ARG_LATITUDE);
            longitude = args.getDouble(ARG_LONGITUDE);
            storeId = args.getLong(ARG_STORE_ID, NO_STORE_ID);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        routeNumberLayout = view.findViewById(R.id.til_route_number);
        storeCodeLayout = view.findViewById(R.id.til_store_code);
        storeNameLayout = view.findViewById(R.id.til_store_name);
        areaLayout = view.findViewById(R.id.til_area);

        routeNumberInput = view.findViewById(R.id.et_route_number);
        storeCodeInput = view.findViewById(R.id.et_store_code);
        storeNameInput = view.findViewById(R.id.et_store_name);
        areaInput = view.findViewById(R.id.et_area);

        coordinatesView = view.findViewById(R.id.tv_store_form_coordinates);

        saveButton = view.findViewById(R.id.btn_store_save);
        cancelButton = view.findViewById(R.id.btn_store_cancel);
        progress = view.findViewById(R.id.store_form_progress);

        TextView title = view.findViewById(R.id.tv_store_form_title);
        title.setText(isEditing() ? R.string.store_form_title_edit : R.string.store_form_title_add);

        saveButton.setOnClickListener(v -> onSaveClicked());
        cancelButton.setOnClickListener(v -> dismiss());

        if (isEditing()) {
            prefillWhenLoaded = savedInstanceState == null;
            setLoading(true);
            loadStore();
        } else {
            coordinatesView.setText(
                    getString(R.string.store_form_coordinates_format, latitude, longitude));
        }
    }

    /** True when this form is editing an existing store rather than creating one. */
    private boolean isEditing() {
        return storeId != NO_STORE_ID;
    }

    // ------------------------------------------------------------------ loading (edit mode)

    /** Reads the store being edited, so the form shows what is actually stored. */
    private void loadStore() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        AlphaRouteApp.from(context).storeRepository().findById(storeId, this::onStoreLoaded);
    }

    private void onStoreLoaded(@Nullable StoreEntity store) {
        if (!isAdded()) {
            // The sheet went away while the read was in flight.
            return;
        }

        if (store == null) {
            // Deleted between the details sheet opening and Edit being tapped. There is nothing to edit,
            // and an empty form would be worse than no form.
            closeWithMessage(R.string.store_missing);
            return;
        }

        // The coordinates are read-only, so they always come from the store itself.
        latitude = store.latitude;
        longitude = store.longitude;
        coordinatesView.setText(
                getString(R.string.store_form_coordinates_format, latitude, longitude));

        if (prefillWhenLoaded) {
            routeNumberInput.setText(store.routeNumber);
            storeCodeInput.setText(store.storeCode);
            storeNameInput.setText(store.storeName);
            areaInput.setText(store.area);
        }

        setLoading(false);
    }

    // ------------------------------------------------------------------ save

    private void onSaveClicked() {
        // Nothing else runs while a write is in flight, or while the store being edited is still being
        // read - saving before the read returns would send a blank form.
        if (saving || loading) {
            return;
        }

        String routeNumber = textOf(routeNumberInput);
        String storeCode = textOf(storeCodeInput);
        String storeName = textOf(storeNameInput);
        String area = textOf(areaInput);

        clearErrors();

        // Stage one: the field rules, synchronously. Blank, too long, or an impossible coordinate.
        StoreValidationResult validation = StoreValidator.validate(
                routeNumber, storeCode, storeName, area, latitude, longitude);

        if (!validation.isValid()) {
            showValidationErrors(validation);
            return;
        }

        setSaving(true);

        StoreEntity store = buildEntity(routeNumber, storeCode, storeName, area);

        Context context = getContext();
        if (context == null) {
            setSaving(false);
            return;
        }

        // Stage two: the duplicate checks, asynchronously, inside the repository.
        StoreRepository repository = AlphaRouteApp.from(context).storeRepository();
        if (isEditing()) {
            repository.update(store, this::onSaveFinished);
        } else {
            repository.insert(store, this::onSaveFinished);
        }
    }

    /**
     * Builds the entity to write.
     *
     * <p>Values are trimmed here as well as inside the repository. The repository's trim is the
     * invariant; this one means the object handed over is already what gets stored.
     *
     * <p><b>In edit mode only the fields this form owns are set.</b> The status, the creation time and
     * the completion time are deliberately left at their defaults on the entity - the repository copies
     * them back from the stored row before writing, which is what makes an edit unable to un-complete a
     * store. See {@code StoreRepository.preserveUneditableFields}.
     */
    private StoreEntity buildEntity(String routeNumber, String storeCode, String storeName, String area) {
        String trimmedRouteNumber = StoreValidator.trimToEmpty(routeNumber);
        String trimmedStoreCode = StoreValidator.trimToEmpty(storeCode);
        String trimmedStoreName = StoreValidator.trimToEmpty(storeName);
        String trimmedArea = StoreValidator.trimToEmpty(area);

        if (isEditing()) {
            StoreEntity store = new StoreEntity();
            store.id = storeId;
            store.routeNumber = trimmedRouteNumber;
            store.storeCode = trimmedStoreCode;
            store.storeName = trimmedStoreName;
            store.area = trimmedArea;
            store.latitude = latitude;
            store.longitude = longitude;
            return store;
        }

        // A new store is always PENDING - the convenience constructor enforces that.
        return new StoreEntity(trimmedRouteNumber, trimmedStoreCode, trimmedStoreName, trimmedArea,
                latitude, longitude, System.currentTimeMillis());
    }

    private void onSaveFinished(@NonNull WriteOutcome outcome) {
        if (!isAdded()) {
            // The sheet went away while the write was in flight. The store is saved; there is simply
            // nobody left to tell.
            return;
        }

        setSaving(false);

        switch (outcome) {
            case SUCCESS:
                if (isEditing()) {
                    // An edit has no other visible confirmation. The marker does not move and does not
                    // change colour, and osmdroid's own marker bubble is suppressed because the map opens
                    // its own sheet on a tap - so without this the screen would simply close and look as
                    // though nothing had happened.
                    showMessage(R.string.store_edit_updated);
                }
                // Nothing is handed back: the map observes the store table, so the marker updates as
                // soon as Room reports the write. See the class note on TAG.
                dismiss();
                break;

            case NOT_FOUND:
                // The store was deleted while the form was open. Nothing was written.
                closeWithMessage(R.string.store_missing);
                break;

            case DUPLICATE_STORE_CODE:
                // Reported on the field it belongs to, rather than as a general error.
                storeCodeLayout.setError(getString(R.string.store_error_code_taken));
                storeCodeInput.requestFocus();
                break;

            case DUPLICATE_ROUTE_NUMBER:
                routeNumberLayout.setError(getString(R.string.store_error_route_number_taken));
                routeNumberInput.requestFocus();
                break;

            default:
                Snackbar.make(requireView(), R.string.store_error_save_failed, Snackbar.LENGTH_LONG).show();
                break;
        }
    }

    // ------------------------------------------------------------------ state

    /**
     * Locks or unlocks the form while a write is in flight.
     *
     * <p>Both buttons are disabled and a progress bar appears, so a second tap has nothing to hit.
     * Tapping outside is also blocked for the same reason.
     */
    private void setSaving(boolean isSaving) {
        saving = isSaving;
        saveButton.setEnabled(!isSaving);
        cancelButton.setEnabled(!isSaving);
        progress.setVisibility(isSaving ? View.VISIBLE : View.GONE);

        if (getDialog() != null) {
            getDialog().setCanceledOnTouchOutside(!isSaving);
        }
    }

    /**
     * Locks the form while an existing store is being read.
     *
     * <p>Only Save is disabled. Cancel stays live, so a slow read can never trap the user behind a form
     * they cannot leave.
     */
    private void setLoading(boolean isLoading) {
        loading = isLoading;
        saveButton.setEnabled(!isLoading);
        progress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ errors

    private void clearErrors() {
        routeNumberLayout.setError(null);
        storeCodeLayout.setError(null);
        storeNameLayout.setError(null);
        areaLayout.setError(null);
    }

    /**
     * Shows every problem at once, under the field it belongs to.
     *
     * <p>All of them, not the first: revealing one error per attempt would make the user go round the
     * form as many times as they have mistakes.
     */
    private void showValidationErrors(@NonNull StoreValidationResult validation) {
        setFieldError(routeNumberLayout, validation.routeNumberError(), R.string.store_field_route_number);
        setFieldError(storeCodeLayout, validation.storeCodeError(), R.string.store_field_store_code);
        setFieldError(storeNameLayout, validation.storeNameError(), R.string.store_field_store_name);
        setFieldError(areaLayout, validation.areaError(), R.string.store_field_area);

        if (validation.coordinatesError() != null) {
            // There is no coordinate field to attach this to, so it goes on the read-only readout.
            Snackbar.make(requireView(), R.string.store_error_coordinates_invalid, Snackbar.LENGTH_LONG).show();
        }

        // Focus the first field with a problem, so the keyboard lands where the work is.
        if (validation.routeNumberError() != null) {
            routeNumberInput.requestFocus();
        } else if (validation.storeCodeError() != null) {
            storeCodeInput.requestFocus();
        } else if (validation.storeNameError() != null) {
            storeNameInput.requestFocus();
        } else if (validation.areaError() != null) {
            areaInput.requestFocus();
        }
    }

    private void setFieldError(@NonNull TextInputLayout layout,
                               @Nullable StoreFieldError error,
                               @StringRes int fieldNameRes) {
        if (error == null) {
            return;
        }
        layout.setError(getString(messageFor(error), getString(fieldNameRes)));
    }

    /**
     * Maps an error code to a message.
     *
     * <p>This is the other half of why {@code StoreValidator} reports codes rather than text: the
     * wording, and any future translation of it, lives entirely here.
     */
    @StringRes
    private static int messageFor(@NonNull StoreFieldError error) {
        switch (error) {
            case TOO_LONG:
                return R.string.store_error_too_long_format;
            case INVALID_COORDINATES:
                return R.string.store_error_coordinates_invalid;
            case REQUIRED:
            default:
                return R.string.store_error_required_format;
        }
    }

    // ------------------------------------------------------------------ messages

    /**
     * Tells the user something, then closes the form.
     *
     * <p>A {@link Toast} rather than a Snackbar, because the sheet is being dismissed in the same
     * breath and a Snackbar is anchored to the view it was shown on - it would vanish before it could
     * be read.
     */
    private void closeWithMessage(@StringRes int messageRes) {
        showMessage(messageRes);
        dismiss();
    }

    private void showMessage(@StringRes int messageRes) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private static String textOf(@Nullable TextView view) {
        return view == null || view.getText() == null ? null : view.getText().toString();
    }
}
