package com.cph.alpharoute.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Unit tests for {@link DateTimeHelper} - the date and time text on the Store Details sheet.
 *
 * <p>These run on the JVM with no emulator, because {@code DateTimeHelper} is pure Java (AD-11).
 *
 * <p><b>Why this deserves tests at all.</b> Formatting a timestamp looks like a one-liner and is not.
 * Every interesting case is an off-by-one-day case, and they are the cases a user would actually hit:
 *
 * <ul>
 *   <li>A store completed at <b>11:50 pm</b> must show that day, not the next one.</li>
 *   <li>A store completed at <b>00:10</b> must show the new day.</li>
 *   <li>The same instant is a <b>different date in a different timezone</b> - so the sheet would show
 *       yesterday's date for a store completed tonight if the zone were handled wrongly.</li>
 * </ul>
 *
 * <p><b>Both the zone and the locale are pinned in every test.</b> If these used
 * {@code ZoneId.systemDefault()} they would pass on this machine and fail on a build server in another
 * timezone, which is worse than having no test at all. The zone used is Asia/Manila - UTC+8 with no
 * daylight saving, so a fixed instant always maps to the same wall clock and the expectations below
 * cannot drift.
 */
public class DateTimeHelperTest {

    /** The app's operating region. UTC+8 all year - the Philippines has no daylight saving. */
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    /** Used only to prove the zone parameter is actually honoured. */
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static final Locale ENGLISH = Locale.ENGLISH;

    /** A wall-clock moment in a given zone, as epoch milliseconds. */
    private static long at(int year, int month, int day, int hour, int minute, ZoneId zone) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
                .toInstant()
                .toEpochMilli();
    }

    // ------------------------------------------------------------------ dates

    @Test
    public void dateText_readsATimestampInTheGivenZone() {
        assertEquals("26 Sep 2026",
                DateTimeHelper.dateText(at(2026, 9, 26, 0, 10, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void dateText_theSameInstantIsADifferentDayInADifferentZone() {
        // 8 pm UTC is already 4 am the NEXT day in Manila. This is the bug this test exists to prevent:
        // a store completed at 4 am would be filed under yesterday.
        long instant = Instant.parse("2026-09-25T20:00:00Z").toEpochMilli();

        assertEquals("25 Sep 2026", DateTimeHelper.dateText(instant, UTC, ENGLISH));
        assertEquals("26 Sep 2026", DateTimeHelper.dateText(instant, MANILA, ENGLISH));
    }

    @Test
    public void dateText_aLateEveningCompletionStaysOnThatDay() {
        // 11:50 pm. The one-minute-away boundary, from the side that must NOT roll over.
        assertEquals("25 Sep 2026",
                DateTimeHelper.dateText(at(2026, 9, 25, 23, 50, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void dateText_justAfterMidnightIsTheNewDay() {
        // 00:10. The same boundary from the other side.
        assertEquals("26 Sep 2026",
                DateTimeHelper.dateText(at(2026, 9, 26, 0, 10, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void dateText_theDayOfTheMonthHasNoLeadingZero() {
        assertEquals("5 Sep 2026",
                DateTimeHelper.dateText(at(2026, 9, 5, 9, 0, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void dateText_epochZeroIsTheStartOfNineteenSeventy() {
        // An independently checkable fact, so a systematic error in the epoch conversion cannot hide
        // behind expectations that were themselves produced by the same conversion.
        assertEquals("1 Jan 1970", DateTimeHelper.dateText(0L, UTC, ENGLISH));
    }

    @Test
    public void dateText_oneBillionSecondsIsTheNinthOfSeptember() {
        // 1,000,000,000 seconds after the epoch is 2001-09-09T01:46:40Z - the "Unix billennium".
        assertEquals("9 Sep 2001", DateTimeHelper.dateText(1_000_000_000_000L, UTC, ENGLISH));
    }

    @Test
    public void dateText_usesTheLocaleForMonthNames() {
        long instant = at(2026, 9, 25, 10, 12, MANILA);

        assertEquals("25 Sep 2026", DateTimeHelper.dateText(instant, MANILA, ENGLISH));
        // Proves the locale parameter is not being ignored. The exact French spelling is deliberately
        // not asserted - it varies between JDK releases - only that it is not the English name.
        assertNotEquals("25 Sep 2026", DateTimeHelper.dateText(instant, MANILA, Locale.FRENCH));
    }

    // ------------------------------------------------------------------ times

    @Test
    public void timeText_usesATwelveHourClockWithAnAmPmMarker() {
        assertEquals("10:12 AM",
                DateTimeHelper.timeText(at(2026, 9, 25, 10, 12, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void timeText_midnightReadsAsTwelveAm() {
        // The classic twelve-hour-clock trap: hour 0 is "12 AM", never "0 AM".
        assertEquals("12:00 AM",
                DateTimeHelper.timeText(at(2026, 9, 25, 0, 0, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void timeText_noonReadsAsTwelvePm() {
        // And hour 12 is "12 PM", never "0 PM".
        assertEquals("12:00 PM",
                DateTimeHelper.timeText(at(2026, 9, 25, 12, 0, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void timeText_theEveningReadsAsPm() {
        assertEquals("6:12 PM",
                DateTimeHelper.timeText(at(2026, 9, 25, 18, 12, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void timeText_theHourHasNoLeadingZeroButTheMinutesDo() {
        // "6:05 AM", not "06:05 AM" and not "6:5 AM". The minutes must stay two digits or the column
        // of times jitters as the sheet is opened for different stores.
        assertEquals("6:05 AM",
                DateTimeHelper.timeText(at(2026, 9, 25, 6, 5, MANILA), MANILA, ENGLISH));
    }

    @Test
    public void timeText_readsATimestampInTheGivenZone() {
        long instant = Instant.parse("2026-09-25T20:00:00Z").toEpochMilli();

        assertEquals("8:00 PM", DateTimeHelper.timeText(instant, UTC, ENGLISH));
        assertEquals("4:00 AM", DateTimeHelper.timeText(instant, MANILA, ENGLISH));
    }

    @Test
    public void timeText_epochZeroIsMidnightUtc() {
        assertEquals("12:00 AM", DateTimeHelper.timeText(0L, UTC, ENGLISH));
    }

    // ------------------------------------------------------------------ the pair

    @Test
    public void dateAndTimeDescribeTheSameMoment() {
        // The sheet shows the two on separate rows, so they must never disagree about which moment they
        // are describing - which is why both come from one private method.
        long instant = at(2026, 9, 25, 22, 45, MANILA);

        assertEquals("25 Sep 2026", DateTimeHelper.dateText(instant, MANILA, ENGLISH));
        assertEquals("10:45 PM", DateTimeHelper.timeText(instant, MANILA, ENGLISH));
    }
}
