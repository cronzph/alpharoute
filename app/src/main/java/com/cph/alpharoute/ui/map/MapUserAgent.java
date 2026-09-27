package com.cph.alpharoute.ui.map;

/**
 * Builds the User-Agent string this app identifies itself with when requesting map tiles.
 *
 * <p><b>This is not cosmetic - it is a hard requirement, and getting it wrong silently breaks the
 * whole map.</b> The OpenStreetMap Foundation's tile usage policy states it plainly:
 *
 * <blockquote>"Many HTTP clients and Software Development Kits use a generic User-Agent header (e.g.
 * {@code okhttp/x.y}, {@code Java/1.8}, {@code curl/x.y}). Traffic that uses these defaults
 * <b>will be blocked</b> because we cannot identify or contact the actual application."</blockquote>
 *
 * <p>An app that shipped osmdroid's default User-Agent would show a blank map, and the cause would be
 * invisible from the code: the requests would simply stop being answered. The policy also requires the
 * identifier to be <b>stable</b> across requests and releases - the version number may change, but the
 * product name must not.
 *
 * <p>Pure Java with zero Android imports, so {@code MapUserAgentTest} can prove the format on the JVM
 * without a device.
 *
 * <p><b>Before any public distribution</b> the user should add their own contact URL or email to the
 * string below. The policy asks for one so the tile operators can reach the app's author if something
 * is wrong. It is deliberately left out here rather than invented.
 */
public final class MapUserAgent {

    /** Utility class - never instantiated. */
    private MapUserAgent() {
    }

    /**
     * The product token. Must stay stable across releases: the policy allows the version to change but
     * not the name, because that is how the tile operators recognise repeat traffic.
     */
    public static final String PRODUCT = "AlphaRoute";

    /** Used when the build supplies no application id. Never expected, but must not produce a blank header. */
    private static final String FALLBACK_APPLICATION_ID = "unknown.application";

    /** Used when the build supplies no version name. */
    private static final String FALLBACK_VERSION = "0";

    /**
     * Builds the User-Agent header value, for example {@code AlphaRoute/1.0 (com.cph.alpharoute)}.
     *
     * <p>The shape follows the convention the policy illustrates - a product token, a version, and a
     * parenthesised identifier - so the string is both unique to this app and readable by a human
     * looking at a server log.
     *
     * @param applicationId the build's application id, normally {@code BuildConfig.APPLICATION_ID}
     * @param versionName   the build's version name, normally {@code BuildConfig.VERSION_NAME}
     */
    public static String build(String applicationId, String versionName) {
        String app = isBlank(applicationId) ? FALLBACK_APPLICATION_ID : applicationId.trim();
        String version = isBlank(versionName) ? FALLBACK_VERSION : versionName.trim();

        return PRODUCT + "/" + version + " (" + app + ")";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
