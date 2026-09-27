package com.cph.alpharoute.domain;

import androidx.annotation.Nullable;

import com.cph.alpharoute.util.GeoConstants;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the location out of a Google Maps link.
 *
 * <p>Pure Java with no Android imports (AD-11), so every format and every boundary can be tested on the
 * JVM. That matters more here than almost anywhere else in the app, because of one specific trap.
 *
 * <p><b>A Google Maps place link contains TWO coordinate pairs, and they are not the same point.</b>
 *
 * <pre>
 * …/maps/place/Alfamart/@14.6010,120.9900,17z/data=…!3d14.599528!4d120.984206…
 *                    └─ the CAMERA ─┘            └─── the PLACE ───┘
 * </pre>
 *
 * <p>The {@code @} pair is where the map was looking; the {@code !3d}/{@code !4d} pair is the place
 * itself. In a test they were <b>164 metres apart</b> - and this app's arrival radius defaults to
 * <b>50 metres</b>. Reading the {@code @} pair - the obvious one, and the one most examples use - would
 * pin the store on the wrong side of the building, and the app would then never once offer to complete
 * it. Nothing would fail. It would simply never say "you have arrived".
 *
 * <p>So the order below is deliberate: {@code !3d}/{@code !4d} first, the {@code @} pair last.
 *
 * <p><b>It also reads the text a share produces, not just a bare URL.</b> Sharing a place from Google
 * Maps puts the place's name on one line and the link on the next, so the clipboard usually holds
 * something like:
 *
 * <pre>
 * Alfamart San Roque
 * https://maps.app.goo.gl/AbCdEf
 * </pre>
 *
 * <p>Every pattern below searches the whole text rather than requiring it to <i>be</i> a URL, so the name
 * line is simply ignored. The one anchored pattern is the bare-coordinate form, which must not match
 * numbers buried inside something longer.
 *
 * <p><b>A short link cannot be read here.</b> {@code maps.app.goo.gl/…} carries no coordinates; it has to
 * be followed to find them, which needs a network and therefore lives in the repository.
 * {@link #isShortLink(String)} is how the caller tells the two apart.
 */
public final class GoogleMapsLinkParser {

    /** Utility class - never instantiated. */
    private GoogleMapsLinkParser() {
    }

    /** The host Google Maps' Share sheet produces today. */
    private static final String SHORT_HOST_CURRENT = "maps.app.goo.gl";

    /** The host it used to produce, and still sometimes does. */
    private static final String SHORT_HOST_LEGACY = "goo.gl/maps/";

    private static final String GEO_SCHEME = "geo:";

    /** A number, with an optional sign and optional decimal part. */
    private static final String NUMBER = "(-?\\d+(?:\\.\\d+)?)";

    /** The place itself: {@code !3d<lat>} and {@code !4d<lng>}. Matched separately, so anything between them is tolerated. */
    private static final Pattern PLACE_LATITUDE = Pattern.compile("!3d" + NUMBER);
    private static final Pattern PLACE_LONGITUDE = Pattern.compile("!4d" + NUMBER);

    /** An explicit query parameter: {@code ?q=}, {@code ?query=}, {@code ?ll=} or {@code ?daddr=}. */
    private static final Pattern QUERY_PARAMETER = Pattern.compile(
            "[?&](?:q|query|ll|daddr)=" + NUMBER + "(?:%2C|,)" + NUMBER,
            Pattern.CASE_INSENSITIVE);

    /** The camera centre: {@code @<lat>,<lng>}. The LEAST accurate source, so it is tried last. */
    private static final Pattern CAMERA_CENTRE = Pattern.compile("@" + NUMBER + "," + NUMBER);

    /** A bare "lat, lng" pasted on its own. Anchored, so it cannot match numbers inside a longer text. */
    private static final Pattern BARE_COORDINATES = Pattern.compile(
            "^" + NUMBER + "\\s*,\\s*" + NUMBER + "$");

    /**
     * Whether this text is a short link, which has to be followed before it can be read.
     *
     * <p>Case-folded with {@link Locale#ROOT} rather than the device locale: a host name is not user text,
     * and in a Turkish locale the default folding turns "I" into a dotless "ı", which is not a letter any
     * host name contains.
     */
    public static boolean isShortLink(@Nullable String text) {
        if (text == null) {
            return false;
        }
        String folded = text.trim().toLowerCase(Locale.ROOT);
        return folded.contains(SHORT_HOST_CURRENT) || folded.contains(SHORT_HOST_LEGACY);
    }

    /**
     * The location in the text, or {@code null} when there is not one.
     *
     * <p>Returns {@code null} for a short link - see the class note. Returns {@code null} for anything
     * else without a usable coordinate pair, rather than guessing: pinning a store in the wrong place is
     * worse than asking the user to paste again.
     *
     * @param text a link, a share blob, or a bare {@code "lat, lng"}
     */
    @Nullable
    public static GeoCoordinate parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // 1. A bare "lat, lng". Unambiguous, and it must be checked before anything that searches, because
        //    the anchored pattern is the only one that can tell it apart from numbers inside a URL.
        GeoCoordinate bare = fromMatch(BARE_COORDINATES.matcher(trimmed));
        if (bare != null) {
            return bare;
        }

        // 2. geo:<lat>,<lng> - the form Android's own "share a location" uses. Anything after a '?' is
        //    ignored, because the same coordinates are repeated there.
        if (trimmed.regionMatches(true, 0, GEO_SCHEME, 0, GEO_SCHEME.length())) {
            String rest = trimmed.substring(GEO_SCHEME.length());
            int query = rest.indexOf('?');
            if (query >= 0) {
                rest = rest.substring(0, query);
            }
            GeoCoordinate geo = fromMatch(BARE_COORDINATES.matcher(rest.trim()));
            if (geo != null) {
                return geo;
            }
        }

        // 3. !3d / !4d - THE PLACE. This is the one that matters; see the class note.
        GeoCoordinate place = fromPair(
                PLACE_LATITUDE.matcher(trimmed), PLACE_LONGITUDE.matcher(trimmed));
        if (place != null) {
            return place;
        }

        // 4. An explicit query parameter - what a "search for these coordinates" link uses.
        GeoCoordinate queried = fromMatch(QUERY_PARAMETER.matcher(trimmed));
        if (queried != null) {
            return queried;
        }

        // 5. The camera centre, LAST. It is a real coordinate and close to the place, but it is the map's
        //    viewpoint rather than the shop, and 164 m of error is four times the arrival radius.
        return fromMatch(CAMERA_CENTRE.matcher(trimmed));
    }

    /** The coordinate in a single two-group match, or {@code null}. */
    @Nullable
    private static GeoCoordinate fromMatch(@Nullable Matcher matcher) {
        if (matcher == null || !matcher.find()) {
            return null;
        }
        return coordinate(matcher.group(1), matcher.group(2));
    }

    /** The coordinate from two separate one-group matches, both of which must be present. */
    @Nullable
    private static GeoCoordinate fromPair(@Nullable Matcher latitude, @Nullable Matcher longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        if (!latitude.find() || !longitude.find()) {
            return null;
        }
        return coordinate(latitude.group(1), longitude.group(1));
    }

    /**
     * Two matched numbers as a coordinate, or {@code null} if they are not a place on Earth.
     *
     * <p>The range check is the same {@link GeoConstants} check the rest of the app uses, so a link cannot
     * pin a store somewhere the store validator would refuse.
     */
    @Nullable
    private static GeoCoordinate coordinate(@Nullable String latitude, @Nullable String longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        try {
            double lat = Double.parseDouble(latitude);
            double lng = Double.parseDouble(longitude);
            if (!GeoConstants.isValidLatitude(lat) || !GeoConstants.isValidLongitude(lng)) {
                return null;
            }
            return new GeoCoordinate(lat, lng);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
