package com.cph.alpharoute.ui.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link MapUserAgent}.
 *
 * <p>These run on the JVM, no device needed, because the class is pure Java.
 *
 * <p><b>Why a User-Agent deserves tests.</b> The OpenStreetMap tile policy states that traffic using a
 * generic library default {@code "will be blocked"}. If this string ever became blank, or reverted to
 * something library-shaped, the map would stop loading - and the symptom would be a blank grey grid,
 * which points nowhere near the cause. These tests make that failure impossible to introduce quietly.
 */
public class MapUserAgentTest {

    private static final String APP_ID = "com.cph.alpharoute";
    private static final String VERSION = "1.0";

    @Test
    public void theHeaderNamesTheApplication() {
        String userAgent = MapUserAgent.build(APP_ID, VERSION);

        assertTrue("The tile servers must be able to tell who is asking: " + userAgent,
                userAgent.contains(APP_ID));
    }

    @Test
    public void theHeaderCarriesTheVersion() {
        String userAgent = MapUserAgent.build(APP_ID, VERSION);

        assertTrue("The policy allows the version to change, so it must be present: " + userAgent,
                userAgent.contains(VERSION));
    }

    @Test
    public void theHeaderUsesTheExpectedShape() {
        // Product token, slash, version, then the parenthesised identifier - the shape the policy's
        // own example uses, which makes the string readable in a server log.
        assertEquals("AlphaRoute/1.0 (com.cph.alpharoute)", MapUserAgent.build(APP_ID, VERSION));
    }

    @Test
    public void theProductTokenIsStable() {
        // The policy requires the identifier to be stable across releases. Only the version may change.
        assertEquals("AlphaRoute", MapUserAgent.PRODUCT);
    }

    @Test
    public void aNullApplicationIdStillProducesAUsableHeader() {
        String userAgent = MapUserAgent.build(null, VERSION);

        assertFalse("A blank header would get the app blocked outright", userAgent.trim().isEmpty());
        assertTrue(userAgent.contains("unknown.application"));
    }

    @Test
    public void aBlankApplicationIdStillProducesAUsableHeader() {
        String userAgent = MapUserAgent.build("   ", VERSION);

        assertFalse(userAgent.trim().isEmpty());
        assertTrue(userAgent.contains("unknown.application"));
    }

    @Test
    public void aNullVersionFallsBackRatherThanLeavingAGap() {
        String userAgent = MapUserAgent.build(APP_ID, null);

        assertTrue("The version slot must never be empty: " + userAgent, userAgent.contains("/0"));
    }

    @Test
    public void aBlankVersionFallsBackRatherThanLeavingAGap() {
        String userAgent = MapUserAgent.build(APP_ID, "  ");

        assertTrue(userAgent.contains("/0"));
    }

    @Test
    public void surroundingWhitespaceIsTrimmed() {
        assertEquals("AlphaRoute/1.0 (com.cph.alpharoute)",
                MapUserAgent.build("  " + APP_ID + "  ", "  1.0  "));
    }

    @Test
    public void theHeaderIsNotAGenericLibraryDefault() {
        // The exact failure the policy names. If any of these ever appears, the map is about to stop
        // working and the cause will be invisible from the screen.
        String userAgent = MapUserAgent.build(APP_ID, VERSION).toLowerCase();

        assertFalse(userAgent.contains("okhttp"));
        assertFalse(userAgent.contains("java/"));
        assertFalse(userAgent.contains("curl"));
        assertFalse(userAgent.contains("dalvik"));
        assertFalse(userAgent.contains("android sdk"));
    }

    @Test
    public void theHeaderIsDeterministic() {
        // "Stable across requests" is a policy requirement, so the same inputs must always give the
        // same string - no timestamps, no random tokens, no device identifiers.
        assertEquals(MapUserAgent.build(APP_ID, VERSION), MapUserAgent.build(APP_ID, VERSION));
    }
}
