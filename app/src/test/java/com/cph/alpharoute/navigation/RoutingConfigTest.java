package com.cph.alpharoute.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link RoutingConfig} - the routing service's address, and whether this build can use it.
 *
 * <p>These run on the JVM with no emulator and no network. Everything checked here is a pure rule about
 * strings, which is deliberate: {@code isUsable} and {@code normalise} take the key as an argument rather
 * than reading {@code BuildConfig} themselves, so the result does not depend on which machine runs the
 * tests. A test that passed on a machine with a key configured and failed on one without would be worse
 * than no test at all.
 *
 * <p><b>Two of these are worth more than the rest.</b>
 *
 * <ul>
 *   <li><b>The key must never be treated as configured when it is blank.</b> The app has to build, install
 *       and run before the account exists, and a request sent with an empty key comes back 401 - an error
 *       that looks like a broken key rather than a missing one. {@code isConfigured()} is what lets the
 *       route screen say "not set up yet" instead.</li>
 *   <li><b>The base URL must be HTTPS.</b> The key travels in the {@code Authorization} header of every
 *       request, so an {@code http://} URL would broadcast it to anyone sharing the network. That is one
 *       character away from being wrong and nothing else in the app would notice.</li>
 * </ul>
 */
public class RoutingConfigTest {

    // ------------------------------------------------------------------ is a key usable?

    @Test
    public void aMissingKeyIsNotUsable() {
        assertFalse(RoutingConfig.isUsable(null));
    }

    @Test
    public void anEmptyKeyIsNotUsable() {
        // What local.properties produces when the key has not been added yet.
        assertFalse(RoutingConfig.isUsable(""));
    }

    @Test
    public void aWhitespaceOnlyKeyIsNotUsable() {
        // Spaces are not a key, and a build constant that is one space is exactly the sort of thing a
        // careless edit leaves behind.
        assertFalse(RoutingConfig.isUsable("   "));
    }

    @Test
    public void aRealKeyIsUsable() {
        assertTrue(RoutingConfig.isUsable("a-real-looking-key"));
    }

    @Test
    public void aKeyWithSurroundingWhitespaceIsStillUsable() {
        // Pasting from a browser or a terminal very often brings a trailing newline along. Rejecting that
        // as "not configured" would send the user looking for a problem that is not there.
        assertTrue(RoutingConfig.isUsable("  a-real-looking-key  "));
    }

    // ------------------------------------------------------------------ what we would actually send

    @Test
    public void aMissingKeyNormalisesToAnEmptyStringNeverNull() {
        // apiKey() promises a string a caller can put straight into a header, so it must never hand back
        // null and make the caller think about it.
        assertEquals("", RoutingConfig.normalise(null));
    }

    @Test
    public void aKeyIsTrimmedBeforeItIsUsed() {
        assertEquals("a-real-looking-key", RoutingConfig.normalise("  a-real-looking-key\n"));
    }

    @Test
    public void whitespaceAloneNormalisesToAnEmptyString() {
        assertEquals("", RoutingConfig.normalise("\t \n"));
    }

    // ------------------------------------------------------------------ where the request goes

    @Test
    public void theBaseUrlIsHttps() {
        // The key is sent in a header on every request. Over http it would be readable by anyone on the
        // same network, and the app would look like it was working perfectly.
        assertTrue("The API key travels in this request, so it must be HTTPS",
                RoutingConfig.BASE_URL.startsWith("https://"));
        assertFalse(RoutingConfig.BASE_URL.startsWith("http://"));
    }

    @Test
    public void theDirectionsUrlIsUnderTheBaseUrl() {
        assertTrue(RoutingConfig.directionsUrl().startsWith(RoutingConfig.BASE_URL));
    }

    @Test
    public void theDirectionsUrlNamesTheDrivingCarProfile() {
        // The app drives between shops. If this ever silently became a walking profile, every duration
        // would be wrong and nothing would fail.
        assertTrue(RoutingConfig.directionsUrl().contains(RoutingConfig.PROFILE_DRIVING_CAR));
    }

    @Test
    public void theOptimizationUrlIsNotUnderV2() {
        // Directions and matrix both live under /v2/, so "/v2/optimization" is the natural guess - and it
        // is wrong. Verified against the live service on 2026-09-27:
        //
        //   POST /v2/optimization  ->  404, a plain nginx page
        //   POST /optimization     ->  200 and a solved route
        //
        // That 404 is what made every optimised route report "Could not build a route". The suffix is
        // asserted literally rather than through BASE_URL, so a change to either half fails this test.
        assertEquals("https://api.openrouteservice.org/optimization", RoutingConfig.optimizationUrl());
        assertFalse("the optimization endpoint is NOT under /v2",
                RoutingConfig.optimizationUrl().contains("/v2/"));
    }

    @Test
    public void theMatrixUrlIsStillUnderV2() {
        // The opposite mistake, pinned so that a fix applied to the line above cannot be applied here by
        // reflex. Matrix and directions really do live under /v2/.
        assertTrue(RoutingConfig.matrixUrl().contains("/v2/matrix/"));
        assertTrue(RoutingConfig.directionsUrl().contains("/v2/directions/"));
    }

    @Test
    public void theAttributionNamesBothTheServiceAndItsData() {
        // The attribution is a licence condition, not decoration: openrouteservice requires its routes to
        // be credited, and the routes are built on OpenStreetMap data. Blanking this would be a quiet
        // licence breach, and nothing else in the app would notice.
        String attribution = RoutingConfig.attribution();

        assertTrue(attribution.contains("openrouteservice"));
        assertTrue(attribution.contains("OpenStreetMap"));
    }

    @Test
    public void theKeyIsNotPartOfTheDirectionsUrl() {
        // Verified against the live service on 2026-09-25: a request with no Authorization header comes
        // back 401 "Authorization field missing", so the key belongs in a header. Older versions of this
        // API accepted ?api_key=..., and a lot of examples online still show it - putting the key in the
        // URL would also mean it ends up in logs and crash reports.
        String url = RoutingConfig.directionsUrl();
        assertFalse(url.contains("api_key"));
        assertFalse(url.contains("key="));
    }
}
