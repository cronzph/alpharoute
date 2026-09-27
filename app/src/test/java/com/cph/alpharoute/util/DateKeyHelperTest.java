package com.cph.alpharoute.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The calendar-day rule behind every completion.
 *
 * <p>Pure JVM (AD-11), with the timezone pinned as a parameter. That is not a nicety: without it these
 * assertions would pass in Manila and fail in London, and a test that depends on where it runs is worse
 * than no test at all.
 *
 * <p>The tests fall into three groups, in descending order of how much they matter:
 *
 * <ol>
 *   <li><b>The midnight boundary.</b> One millisecond either side of local midnight must produce
 *       different days. A completion mis-filed here is wrong permanently - nothing downstream can
 *       detect it, because the timestamp and the day both look plausible.</li>
 *   <li><b>The timezone.</b> The same instant is a different calendar day in different places, and the
 *       key must follow the device rather than UTC.</li>
 *   <li><b>The format.</b> Zero-padded, so the keys sort as text - which is what makes
 *       {@code ORDER BY completionDateKey DESC} work in SQL with no conversion.</li>
 * </ol>
 *
 * <p><b>Anchor values are hardcoded on purpose.</b> Building the input with {@code ZonedDateTime} and
 * comparing against {@code ZonedDateTime} would only prove that java.time agrees with itself. The epoch
 * millis below were computed independently, so a wrong zone or a wrong format fails here.
 */
public class DateKeyHelperTest {

    /** UTC+8, no daylight saving - the device's own zone in the Philippines. */
    private static final ZoneId MANILA = ZoneId.of("Asia/Manila");

    /** UTC+0, for the contrast cases. */
    private static final ZoneId UTC = ZoneId.of("UTC");

    /** 2026-09-25T12:00:00Z. Midday UTC, so both zones agree it is the 25th. */
    private static final long NOON_UTC_25_SEP = 1_790_337_600_000L;

    /**
     * 2026-09-25T15:59:59.999Z - one millisecond before local midnight in Manila.
     *
     * <p>In UTC this is still the 25th, and so is it in Manila. The pair of constants below is the whole
     * point of this class.
     */
    private static final long ONE_MS_BEFORE_MANILA_MIDNIGHT = 1_790_351_999_999L;

    /** 2026-09-25T16:00:00Z - local midnight in Manila, so already the 26th there. */
    private static final long MANILA_MIDNIGHT = 1_790_352_000_000L;

    // ------------------------------------------------------------------ the anchors

    @Test
    public void aKnownInstantGivesAKnownDayInManila() {
        assertEquals("2026-09-25", DateKeyHelper.keyOf(NOON_UTC_25_SEP, MANILA));
    }

    @Test
    public void theSameInstantGivesAKnownDayInUtc() {
        assertEquals("2026-09-25", DateKeyHelper.keyOf(NOON_UTC_25_SEP, UTC));
    }

    // ------------------------------------------------------------------ the midnight boundary

    @Test
    public void oneMillisecondBeforeManilaMidnightIsStillTheTwentyFifth() {
        assertEquals("2026-09-25", DateKeyHelper.keyOf(ONE_MS_BEFORE_MANILA_MIDNIGHT, MANILA));
    }

    @Test
    public void exactlyAtManilaMidnightIsAlreadyTheTwentySixth() {
        // The single most consequential assertion in this file. A store completed at 00:00:00.000 must be
        // filed under the new day - and it is the *same* store, minutes after the previous test's moment.
        assertEquals("2026-09-26", DateKeyHelper.keyOf(MANILA_MIDNIGHT, MANILA));
    }

    @Test
    public void consecutiveMillisecondsAcrossMidnightAreDifferentDays() {
        // Stated as a property rather than two constants, so the boundary is asserted where it actually
        // falls rather than where the constants happen to sit.
        String before = DateKeyHelper.keyOf(MANILA_MIDNIGHT - 1L, MANILA);
        String after = DateKeyHelper.keyOf(MANILA_MIDNIGHT, MANILA);
        assertTrue("midnight must change the day", !before.equals(after));
        assertEquals("2026-09-25", before);
        assertEquals("2026-09-26", after);
    }

    @Test
    public void theManilaMidnightInstantIsStillTheTwentyFifthInUtc() {
        // 16:00 UTC is 4 pm in London and midnight in Manila. This is what "local day" means, and why the
        // key is stored rather than derived from the timestamp on every read.
        assertEquals("2026-09-25", DateKeyHelper.keyOf(MANILA_MIDNIGHT, UTC));
        assertEquals("2026-09-26", DateKeyHelper.keyOf(MANILA_MIDNIGHT, MANILA));
    }

    // ------------------------------------------------------------------ the format

    @Test
    public void singleDigitMonthsAndDaysAreZeroPadded() {
        long fifthOfJanuary = ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, MANILA)
                .toInstant().toEpochMilli();
        assertEquals("2026-01-05", DateKeyHelper.keyOf(fifthOfJanuary, MANILA));
    }

    @Test
    public void keysSortAsTextInChronologicalOrder() {
        // The reason for zero padding. Without it, "2026-9-25" would sort before "2026-10-01" and the
        // History screen would group days in a nonsense order.
        String earlyInMonth = DateKeyHelper.keyOf(
                ZonedDateTime.of(2026, 9, 5, 9, 0, 0, 0, MANILA).toInstant().toEpochMilli(), MANILA);
        String lateInMonth = DateKeyHelper.keyOf(
                ZonedDateTime.of(2026, 9, 25, 9, 0, 0, 0, MANILA).toInstant().toEpochMilli(), MANILA);

        assertTrue("2026-09-05 must sort before 2026-09-25",
                earlyInMonth.compareTo(lateInMonth) < 0);
    }

    @Test
    public void aKeyIsAlwaysTenCharacters() {
        // YYYY-MM-DD. Asserted so a format change that dropped padding would fail loudly rather than
        // quietly reordering the history.
        assertEquals(10, DateKeyHelper.keyOf(NOON_UTC_25_SEP, MANILA).length());
        assertEquals(10, DateKeyHelper.keyOf(
                ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, MANILA).toInstant().toEpochMilli(),
                MANILA).length());
    }

    // ------------------------------------------------------------------ shiftDays

    @Test
    public void shiftingBackOneDayCrossesIntoThePreviousDay() {
        assertEquals("2026-09-24", DateKeyHelper.shiftDays("2026-09-25", -1));
    }

    @Test
    public void shiftingForwardOneDayCrossesIntoTheNextDay() {
        assertEquals("2026-09-26", DateKeyHelper.shiftDays("2026-09-25", 1));
    }

    @Test
    public void shiftingZeroDaysChangesNothing() {
        assertEquals("2026-09-25", DateKeyHelper.shiftDays("2026-09-25", 0));
    }

    @Test
    public void shiftingBackFromTheFirstOfTheMonthCrossesTheMonthBoundary() {
        assertEquals("2026-08-31", DateKeyHelper.shiftDays("2026-09-01", -1));
    }

    @Test
    public void shiftingForwardFromTheLastOfTheMonthCrossesTheMonthBoundary() {
        // September has 30 days, so the 30th is the last one.
        assertEquals("2026-10-01", DateKeyHelper.shiftDays("2026-09-30", 1));
    }

    @Test
    public void shiftingForwardFromNewYearsEveCrossesTheYear() {
        assertEquals("2027-01-01", DateKeyHelper.shiftDays("2026-12-31", 1));
    }

    @Test
    public void shiftingBackFromNewYearsDayCrossesTheYear() {
        assertEquals("2025-12-31", DateKeyHelper.shiftDays("2026-01-01", -1));
    }

    @Test
    public void theLeapDayIsReal() {
        // 2028 is a leap year. This is the case that millisecond arithmetic gets wrong, and the reason
        // shiftDays works on LocalDate rather than by subtracting 86,400,000 ms.
        assertEquals("2028-02-29", DateKeyHelper.shiftDays("2028-02-28", 1));
        assertEquals("2028-03-01", DateKeyHelper.shiftDays("2028-02-29", 1));
    }

    @Test
    public void aNonLeapYearSkipsTheTwentyNinth() {
        assertEquals("2026-03-01", DateKeyHelper.shiftDays("2026-02-28", 1));
    }

    @Test
    public void shiftingForwardAndBackReturnsToTheStart() {
        // Round-tripping catches an off-by-one in either direction, whichever one a hand-written
        // assertion happened to miss.
        String start = "2026-03-01";
        assertEquals(start, DateKeyHelper.shiftDays(DateKeyHelper.shiftDays(start, 37), -37));
    }

    @Test
    public void anInvalidKeyIsRejectedRatherThanSilentlyReturned() {
        // A malformed key can only come from a corrupt row or a caller bug. Returning the input would hide
        // it, and this value decides which day a completion belongs to - a wrong answer is worse than an
        // error, so it throws.
        try {
            DateKeyHelper.shiftDays("25/09/2026", 1);
            fail("Expected an IllegalArgumentException for a non-ISO key");
        } catch (IllegalArgumentException expected) {
            // Correct.
        }
    }

    @Test
    public void anEmptyKeyIsRejected() {
        try {
            DateKeyHelper.shiftDays("", 1);
            fail("Expected an IllegalArgumentException for an empty key");
        } catch (IllegalArgumentException expected) {
            // Correct.
        }
    }

    // ------------------------------------------------------------------ yesterday

    @Test
    public void yesterdayIsOneDayBeforeToday() {
        // Asserted as a relationship rather than against a fixed date, because "today" is genuinely
        // whatever day the test runs on. The relationship is what the Dashboard will depend on.
        String today = DateKeyHelper.today(MANILA);
        String yesterday = DateKeyHelper.yesterday(MANILA);

        assertEquals(today, DateKeyHelper.shiftDays(yesterday, 1));
        assertTrue("yesterday must sort before today", yesterday.compareTo(today) < 0);
    }

    @Test
    public void todayMatchesTheDirectConversion() {
        // The two ways of asking must agree, or the Dashboard's "today" and a completion's day could
        // disagree at midnight.
        assertEquals(
                DateKeyHelper.keyOf(System.currentTimeMillis(), MANILA),
                DateKeyHelper.today(MANILA));
    }
}
