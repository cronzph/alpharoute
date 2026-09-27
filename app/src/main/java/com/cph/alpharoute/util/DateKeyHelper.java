package com.cph.alpharoute.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Turns a moment into the local calendar day it belongs to, as {@code YYYY-MM-DD}.
 *
 * <p><b>Why this is its own class and not a line inside the completion transaction.</b> The completion
 * transaction runs on a disk thread inside Room, where no test can reach it. The date key is the one part
 * of that transaction with a genuine off-by-one risk - a store completed at 11:50 pm must be counted
 * against that day, and one completed at 00:10 against the next - and a mistake there would mis-file a
 * completion permanently, with nothing downstream able to notice. So it lives here, pure Java with zero
 * Android imports (AD-11), and is tested on the JVM at the exact boundaries.
 *
 * <p><b>Stored, never recomputed.</b> The completion row keeps the key rather than deriving it from the
 * timestamp on read. That is what makes "completed today" stable: if the user later flies to another
 * timezone, yesterday's completion must not silently move to a different day.
 *
 * <p><b>The pattern is pinned to {@link Locale#US} and that matters.</b> A formatter built from the
 * device locale can produce non-ASCII digits, and some locales carry a different calendar system - the
 * Thai Buddhist calendar would render 2026 as 2569. A date key is an internal identifier that is compared
 * and sorted as a string, so it must always come out the same shape. Only the <i>zone</i> follows the
 * device, because only the zone is genuinely local.
 *
 * <p><b>The format sorts correctly as text.</b> {@code YYYY-MM-DD} is the one common date format where
 * plain string comparison gives chronological order, which is what lets
 * {@code ORDER BY completionDateKey DESC} work in SQL with no conversion at all.
 */
public final class DateKeyHelper {

    /**
     * The stored key format.
     *
     * <p>Zero-padded on purpose, unlike the display format in {@link DateTimeHelper}. Display text wants
     * {@code 25 Sep 2026}; a key wants {@code 2026-09-25}, because padding is what makes
     * {@code 2026-09-05} sort before {@code 2026-09-25} instead of after it.
     */
    private static final DateTimeFormatter KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);

    /** Utility class - never instantiated. */
    private DateKeyHelper() {
    }

    /**
     * The local calendar day of a moment, in the device's own timezone.
     *
     * @param epochMillis milliseconds since 1 January 1970 UTC
     * @return {@code YYYY-MM-DD}, never {@code null}
     */
    public static String keyOf(long epochMillis) {
        return keyOf(epochMillis, ZoneId.systemDefault());
    }

    /**
     * The calendar day of a moment, in a specific timezone.
     *
     * <p>The zone is a parameter so a test can pin it. Without that, a test asserting "11:50 pm is still
     * today" would pass in Manila and fail in London, and a test that depends on where it runs is worse
     * than no test.
     */
    public static String keyOf(long epochMillis, ZoneId zone) {
        return KEY_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    /** Today's key, in the device's own timezone. */
    public static String today() {
        return today(ZoneId.systemDefault());
    }

    /** Today's key, in a specific timezone. */
    public static String today(ZoneId zone) {
        return keyOf(System.currentTimeMillis(), zone);
    }

    /**
     * Yesterday's key, in a specific timezone.
     *
     * <p>Built by shifting the calendar day rather than by subtracting 86,400,000 milliseconds. Those two
     * are not the same thing: on a daylight-saving changeover the day is 23 or 25 hours long, and the
     * arithmetic version lands on the wrong date.
     */
    public static String yesterday(ZoneId zone) {
        return shiftDays(today(zone), -1);
    }

    /** Yesterday's key, in the device's own timezone. */
    public static String yesterday() {
        return yesterday(ZoneId.systemDefault());
    }

    /**
     * Moves a date key by a number of calendar days.
     *
     * <p>Calendar arithmetic, not millisecond arithmetic - see {@link #yesterday}. A negative number
     * moves backwards. Leap days, month ends and year ends are all handled by {@link LocalDate}.
     *
     * @param dateKey a {@code YYYY-MM-DD} key, as produced by {@link #keyOf}
     * @param days    how many days to move; negative moves backwards
     * @return the shifted key
     * @throws IllegalArgumentException if {@code dateKey} is not a valid {@code YYYY-MM-DD} date
     */
    public static String shiftDays(String dateKey, int days) {
        try {
            return KEY_FORMAT.format(LocalDate.parse(dateKey, KEY_FORMAT).plusDays(days));
        } catch (DateTimeParseException e) {
            // A malformed key can only come from a corrupt row or a caller bug, and silently returning
            // the input would hide it. Failing loudly is the right trade here: this value decides which
            // day a completion belongs to, and a wrong answer is worse than an error.
            throw new IllegalArgumentException("Not a YYYY-MM-DD date key: " + dateKey, e);
        }
    }
}
