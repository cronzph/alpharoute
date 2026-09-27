package com.cph.alpharoute.util;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Turns an epoch-millisecond timestamp into the date and time text a person reads.
 *
 * <p><b>Why this exists as its own class.</b> The Store Details sheet has to show when a store was
 * completed, and a timestamp is exactly the kind of thing that looks right in one timezone and is wrong
 * in another. The interesting cases are all off-by-one-day cases: a store completed at 11:50 pm must
 * read as that day, not the next one, and a store completed at 00:10 must read as the new day. Those
 * cases are testable, so they are tested - which means the formatting lives here, in a class with no
 * Android imports at all, rather than inline in a Fragment where no test can reach it.
 * (Architecture decision AD-11: pure Java in the utility and domain layers.)
 *
 * <p><b>The zone and the locale are parameters, not assumptions.</b> The convenience overloads below
 * use the device's own zone and locale, which is what the app wants. The three-argument versions exist
 * so a test can pin both and get the same answer on every machine in every timezone - otherwise
 * {@code DateTimeHelperTest} would pass in Manila and fail in London, and a test that depends on where
 * it runs is worse than no test.
 *
 * <p><b>The patterns are fixed, and that is deliberate.</b> {@code "d MMM yyyy"} and {@code "h:mm a"}
 * are used rather than {@code DateFormat.getDateFormat()} so the sheet cannot reflow into a different
 * shape on a device with different regional settings. The locale is still passed through, because it
 * decides the month names and the AM/PM marker, and those should follow the device.
 */
public final class DateTimeHelper {

    /** Utility class - never instantiated. */
    private DateTimeHelper() {
    }

    /**
     * Day, abbreviated month, year - for example {@code 25 Sep 2026}.
     *
     * <p>{@code d} rather than {@code dd} so the day has no leading zero, and {@code MMM} rather than
     * {@code MMMM} so a long month name cannot wrap the row on a narrow screen.
     */
    private static final String DATE_PATTERN = "d MMM yyyy";

    /**
     * The same date without the year, for the round report's day rows.
     *
     * <p>{@code d MMM} rather than {@code dd MMM}: "1 Sep" is how a date is written in a sentence, and the
     * leading zero would be the only zero-padded number on the screen.
     */
    private static final String SHORT_DATE_PATTERN = "d MMM";

    /**
     * Twelve-hour clock with a minute, for example {@code 10:12 AM}.
     *
     * <p>{@code h} rather than {@code HH} because this is read by a person, not parsed by a machine -
     * {@code 6:12 PM} is easier to scan than {@code 18:12}, and the AM/PM marker removes the ambiguity
     * that a bare 12-hour clock would have.
     */
    private static final String TIME_PATTERN = "h:mm a";

    /**
     * The date of a timestamp, in the device's own timezone and locale.
     *
     * @param epochMillis milliseconds since 1 January 1970 UTC
     */
    public static String dateText(long epochMillis) {
        return dateText(epochMillis, ZoneId.systemDefault(), Locale.getDefault());
    }

    /**
     * The date of a timestamp, in a specific timezone and locale.
     *
     * @param zone   the timezone the timestamp is to be read in - the device's own in production
     * @param locale decides the month name, e.g. "Sep"
     */
    public static String dateText(long epochMillis, ZoneId zone, Locale locale) {
        return format(epochMillis, zone, locale, DATE_PATTERN);
    }

    /**
     * The time of day of a timestamp, in the device's own timezone and locale.
     *
     * @param epochMillis milliseconds since 1 January 1970 UTC
     */
    public static String timeText(long epochMillis) {
        return timeText(epochMillis, ZoneId.systemDefault(), Locale.getDefault());
    }

    /**
     * The time of day of a timestamp, in a specific timezone and locale.
     *
     * @param zone   the timezone the timestamp is to be read in - the device's own in production
     * @param locale decides the AM/PM marker
     */
    public static String timeText(long epochMillis, ZoneId zone, Locale locale) {
        return format(epochMillis, zone, locale, TIME_PATTERN);
    }

    /**
     * One place where a timestamp becomes text, so the two public methods cannot drift apart.
     *
     * <p>An {@link Instant} is an absolute moment; {@code atZone} is what gives it a calendar day and a
     * wall-clock time. That step is the whole reason this class exists - the instant itself has no date.
     */
    private static String format(long epochMillis, ZoneId zone, Locale locale, String pattern) {
        ZonedDateTime moment = Instant.ofEpochMilli(epochMillis).atZone(zone);
        return DateTimeFormatter.ofPattern(pattern, locale).format(moment);
    }

    /**
     * The readable date of a stored {@code YYYY-MM-DD} day key.
     *
     * <p><b>Why this formats the key rather than a timestamp.</b> The History screen groups by the stored
     * key, so the date it displays must be derived from that same key. Formatting a timestamp from one of
     * the group's rows would usually agree - but if the device's timezone changed after the completion was
     * recorded, it could render as the neighbouring day, and the group would then be labelled with a date
     * that does not match the day it is showing.
     *
     * @param dateKey a {@code YYYY-MM-DD} key, as written by {@code DateKeyHelper}
     * @return the date in the device's locale, e.g. {@code 25 Sep 2026}, or the key itself when it cannot
     *         be parsed. Returning the key is deliberate: a malformed key is still more useful on screen
     *         than an empty label, and the alternative - throwing - would crash a list for one bad row
     */
    public static String dateTextFromKey(@Nullable String dateKey) {
        if (dateKey == null || dateKey.isEmpty()) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE);
            return DateTimeFormatter.ofPattern(DATE_PATTERN, Locale.getDefault()).format(date);
        } catch (DateTimeParseException e) {
            return dateKey;
        }
    }

    /**
     * The same date without the year: {@code 25 Sep}.
     *
     * <p><b>For the round report's day rows, where the year is already stated once.</b> "Day 1 · 1 Sep" reads
     * cleanly inside a card whose header says "1 Sep 2026 – 4 Sep 2026"; repeating the year on every row
     * would be four characters of noise per line and would wrap the title on a narrow screen.
     *
     * <p>Deliberately has <b>no fallback to the key</b> like {@link #dateTextFromKey} does: this is only ever
     * called from a round report whose range line has already shown the full date, so an unparseable key
     * means the whole report is suspect. Returning the raw key keeps that visible rather than hiding it
     * behind an empty string.
     *
     * @param dateKey a {@code YYYY-MM-DD} key
     */
    public static String shortDateTextFromKey(@Nullable String dateKey) {
        if (dateKey == null || dateKey.isEmpty()) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE);
            return DateTimeFormatter.ofPattern(SHORT_DATE_PATTERN, Locale.getDefault()).format(date);
        } catch (DateTimeParseException e) {
            return dateKey;
        }
    }
}
