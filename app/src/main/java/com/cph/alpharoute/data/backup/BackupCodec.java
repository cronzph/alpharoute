package com.cph.alpharoute.data.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cph.alpharoute.data.db.AppSettingEntity;
import com.cph.alpharoute.data.db.CompletionHistoryEntity;
import com.cph.alpharoute.data.db.StoreEntity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the backup file.
 *
 * <p><b>The format, and why it is this shape.</b> JSON, with the three tables side by side, and completions
 * referencing stores by {@code storeCode} rather than by row id.
 *
 * <p><b>No API key, no credential, nothing secret.</b> The roadmap is explicit, and it is the reason this
 * class is written by hand rather than by serialising whatever happens to be in memory: the export contains
 * exactly the six things listed below and nothing else. {@code RoutingConfig.apiKey()} lives in
 * {@code BuildConfig}, never in the database, so it could not leak here even by accident - but the file is
 * also written from an explicit field list, so a future column added to {@code app_settings} would not
 * silently start appearing in backups.
 *
 * <h3>What is in the file</h3>
 *
 * <pre>
 *   formatVersion   1. Bumped when the shape changes, so an old file is refused rather than misread
 *   exportedAt      when it was written, for the user's benefit
 *   settings        Home, the arrival radius, the current round
 *   stores          every store, with its coordinates and status
 *   completions     every completion, pointing at a storeCode and carrying its round
 * </pre>
 *
 * <p><b>{@code lastSelectedStoreId} is deliberately absent.</b> It is a transient reference to a drive in
 * progress, and restoring it into a different database could point at a store that means something else
 * there.
 *
 * <p>Pure apart from {@code org.json}.
 */
public final class BackupCodec {

    /**
     * The version this build writes and the newest it can read.
     *
     * <p>An older file is refused rather than guessed at. Reading a format the app does not know is how a
     * restore silently loses a field - and the user would not find out until the data was already replaced.
     */
    public static final int FORMAT_VERSION = 1;

    private static final String KEY_FORMAT_VERSION = "formatVersion";
    private static final String KEY_EXPORTED_AT = "exportedAt";
    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_STORES = "stores";
    private static final String KEY_COMPLETIONS = "completions";

    /** Utility class - never instantiated. */
    private BackupCodec() {
    }

    // ------------------------------------------------------------------ writing

    /**
     * Writes the whole database as JSON.
     *
     * @param stores     every store
     * @param completions every completion
     * @param settings   the settings row, or {@code null} if it is somehow missing
     * @param exportedAt when the backup was taken
     * @return the file contents
     */
    @NonNull
    public static String toJson(@Nullable List<StoreEntity> stores,
                                @Nullable List<CompletionHistoryEntity> completions,
                                @Nullable AppSettingEntity settings,
                                long exportedAt) throws JSONException {

        JSONObject root = new JSONObject();
        root.put(KEY_FORMAT_VERSION, FORMAT_VERSION);
        root.put(KEY_EXPORTED_AT, exportedAt);
        root.put(KEY_SETTINGS, settingsJson(settings));
        root.put(KEY_STORES, storesJson(stores));
        root.put(KEY_COMPLETIONS, completionsJson(completions, stores));

        return root.toString(2);
    }

    @NonNull
    private static JSONObject settingsJson(@Nullable AppSettingEntity settings) throws JSONException {
        JSONObject json = new JSONObject();
        if (settings == null) {
            // A database with no settings row. Written as defaults rather than omitted, so a reader never
            // has to decide what a missing block means.
            json.put("homeSet", false);
            json.put("arrivalRadius", 0);
            json.put("currentRound", 1);
            return json;
        }

        // Absent rather than null when Home is not set, so the file reads cleanly.
        if (settings.homeLatitude != null) {
            json.put("homeLatitude", settings.homeLatitude);
        }
        if (settings.homeLongitude != null) {
            json.put("homeLongitude", settings.homeLongitude);
        }
        json.put("homeSet", settings.isHomeSet());
        json.put("arrivalRadius", settings.arrivalRadius);
        json.put("currentRound", settings.currentRound);
        return json;
    }

    @NonNull
    private static JSONArray storesJson(@Nullable List<StoreEntity> stores) throws JSONException {
        JSONArray array = new JSONArray();
        if (stores == null) {
            return array;
        }
        for (StoreEntity store : stores) {
            if (store == null) {
                continue;
            }
            JSONObject json = new JSONObject();
            json.put("routeNumber", store.routeNumber);
            json.put("storeCode", store.storeCode);
            json.put("storeName", store.storeName);
            json.put("area", store.area);
            json.put("latitude", store.latitude);
            json.put("longitude", store.longitude);
            // The enum NAME, matching the column. A reader that did not understand it refuses the file
            // rather than guessing.
            json.put("status", store.status == null ? "" : store.status.name());
            json.put("createdAt", store.createdAt);
            json.put("updatedAt", store.updatedAt);
            if (store.completedAt != null) {
                json.put("completedAt", store.completedAt);
            }
            array.put(json);
        }
        return array;
    }

    /**
     * Writes the completions, translating each {@code storeId} into a {@code storeCode}.
     *
     * <p><b>The translation is the important part.</b> Row ids are auto-generated, so a backup's ids mean
     * nothing in the database it is restored into. The code is the app's own stable identity for a store.
     *
     * <p>A completion whose store cannot be found is skipped rather than written with a dangling id - the
     * file must not contain a reference that nothing can resolve.
     */
    @NonNull
    private static JSONArray completionsJson(@Nullable List<CompletionHistoryEntity> completions,
                                             @Nullable List<StoreEntity> stores)
            throws JSONException {
        JSONArray array = new JSONArray();
        if (completions == null) {
            return array;
        }

        java.util.Map<Long, String> codeById = new java.util.HashMap<>();
        if (stores != null) {
            for (StoreEntity store : stores) {
                if (store != null) {
                    codeById.put(store.id, store.storeCode);
                }
            }
        }

        for (CompletionHistoryEntity completion : completions) {
            if (completion == null) {
                continue;
            }
            String storeCode = codeById.get(completion.storeId);
            if (storeCode == null) {
                // An orphan. The foreign key makes this impossible, and writing it would put an
                // unresolvable reference in the file.
                continue;
            }

            JSONObject json = new JSONObject();
            json.put("storeCode", storeCode);
            json.put("completionTimestamp", completion.completionTimestamp);
            json.put("completionDateKey", completion.completionDateKey);
            json.put("dailySequence", completion.dailySequence);
            json.put("roundNumber", completion.roundNumber);
            array.put(json);
        }
        return array;
    }

    // ------------------------------------------------------------------ reading

    /**
     * Reads a backup file.
     *
     * <p><b>It does not validate.</b> Everything here is what the file <i>said</i>, including values that
     * are wrong. Validation is a separate, pure step ({@code BackupValidator}) so that "what the file
     * contained" and "is it usable" are two different questions with two different answers - and so the
     * second one can be tested without a file.
     *
     * @return the contents, or {@code null} when the text is not a backup file at all
     */
    @Nullable
    public static BackupContents parse(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject root = new JSONObject(json);

            // A file without a format version is not one of ours.
            if (!root.has(KEY_FORMAT_VERSION)) {
                return null;
            }

            int formatVersion = root.getInt(KEY_FORMAT_VERSION);
            long exportedAt = root.optLong(KEY_EXPORTED_AT, 0L);

            return new BackupContents(
                    formatVersion,
                    exportedAt,
                    parseSettings(root.optJSONObject(KEY_SETTINGS)),
                    parseStores(root.optJSONArray(KEY_STORES)),
                    parseCompletions(root.optJSONArray(KEY_COMPLETIONS)));

        } catch (JSONException e) {
            return null;
        }
    }

    @Nullable
    private static BackupContents.BackupSettings parseSettings(@Nullable JSONObject json) {
        if (json == null) {
            return null;
        }
        return new BackupContents.BackupSettings(
                json.has("homeLatitude") ? json.optDouble("homeLatitude") : null,
                json.has("homeLongitude") ? json.optDouble("homeLongitude") : null,
                json.optBoolean("homeSet", false),
                json.optInt("arrivalRadius", 0),
                json.optInt("currentRound", 1));
    }

    @NonNull
    private static List<BackupContents.BackupStore> parseStores(@Nullable JSONArray array) {
        List<BackupContents.BackupStore> stores = new ArrayList<>();
        if (array == null) {
            return stores;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) {
                continue;
            }
            stores.add(new BackupContents.BackupStore(
                    json.optString("routeNumber"),
                    json.optString("storeCode"),
                    json.optString("storeName"),
                    json.optString("area"),
                    json.optDouble("latitude", Double.NaN),
                    json.optDouble("longitude", Double.NaN),
                    json.optString("status"),
                    json.optLong("createdAt", 0L),
                    json.optLong("updatedAt", 0L),
                    json.has("completedAt") ? json.optLong("completedAt") : null));
        }
        return stores;
    }

    @NonNull
    private static List<BackupContents.BackupCompletion> parseCompletions(@Nullable JSONArray array) {
        List<BackupContents.BackupCompletion> completions = new ArrayList<>();
        if (array == null) {
            return completions;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) {
                continue;
            }
            completions.add(new BackupContents.BackupCompletion(
                    json.optString("storeCode"),
                    json.optLong("completionTimestamp", 0L),
                    json.optString("completionDateKey"),
                    json.optInt("dailySequence", 0),
                    json.optInt("roundNumber", 1)));
        }
        return completions;
    }
}
