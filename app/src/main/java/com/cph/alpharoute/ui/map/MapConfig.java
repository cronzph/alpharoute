package com.cph.alpharoute.ui.map;

import android.content.Context;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.XYTileSource;

/**
 * Every piece of map configuration, in one place.
 *
 * <p>Called {@code MapTileConfig} in the Phase 0.2 architecture. The name was widened because it also
 * owns the default camera position, and a class called "TileConfig" holding a camera default would be
 * misleading.
 *
 * <p><b>The OpenStreetMap tile usage policy is implemented here</b>, and it is the reason this class
 * exists at all rather than the settings being scattered through {@code MapFragment}. The policy has
 * four requirements on an app like this one, and all four are satisfied in this file:
 *
 * <ol>
 *   <li><b>A distinct, stable User-Agent</b> - see {@link MapUserAgent}. Without it, tile requests are
 *       blocked outright.</li>
 *   <li><b>The correct tile URL</b> - {@code https://tile.openstreetmap.org/{z}/{x}/{y}.png}, exactly,
 *       over HTTPS. The policy warns that "other subdomains or hostnames may be slower or withdrawn
 *       without notice", which is why the URL is written down explicitly here rather than inherited
 *       from whatever a library default happens to be.</li>
 *   <li><b>Visible attribution</b> - the copyright text below is shown on the map by
 *       {@code MapFragment}.</li>
 *   <li><b>Local caching</b> - osmdroid caches tiles to disk, and the bounds below keep that cache
 *       large enough to avoid re-downloading and small enough not to fill the device.</li>
 * </ol>
 *
 * <p><b>What this app must never do</b> (the policy's prohibitions): pre-seed or bulk-download tiles,
 * offer an "offline maps" download button, request tiles over plain HTTP, or use a generic User-Agent.
 * Nothing in this project does any of those, and there is deliberately no prefetch code anywhere.
 *
 * <p><b>Swapping tile providers</b> is a one-line change: {@link #OSM_TILE_BASE_URL} and
 * {@link #TILE_SOURCE_NAME}. The policy recommends not hard-coding the URL throughout the codebase, and
 * it appears exactly once.
 */
public final class MapConfig {

    /** Utility class - never instantiated. */
    private MapConfig() {
    }

    // ------------------------------------------------------------------ tile source

    /**
     * The one place the tile host is written down.
     *
     * <p>Exactly the URL the OpenStreetMap policy requires, over HTTPS. Do not add a subdomain, and do
     * not switch to {@code http://}.
     */
    private static final String OSM_TILE_BASE_URL = "https://tile.openstreetmap.org/";

    /** A name for the tile source, shown in osmdroid debug output. */
    private static final String TILE_SOURCE_NAME = "OpenStreetMap";

    /** The attribution the licence requires to be visible on the map. */
    private static final String OSM_COPYRIGHT = "© OpenStreetMap contributors";

    /** Zoom levels OpenStreetMap serves. */
    private static final int MIN_ZOOM = 0;
    private static final int MAX_ZOOM = 19;

    /** Standard OSM tile edge length in pixels. */
    private static final int TILE_SIZE_PX = 256;

    // ------------------------------------------------------------------ tile cache

    /**
     * Upper bound on the on-disk tile cache.
     *
     * <p>The policy requires caching so repeat views are not re-downloaded; it also forbids unlimited
     * growth. 200 MB holds a great many tiles for a single city, and the trim below keeps the cache
     * from ever filling the device.
     */
    private static final long TILE_CACHE_MAX_BYTES = 200L * 1024L * 1024L;

    /** What the cache is reduced to once it passes the maximum above. */
    private static final long TILE_CACHE_TRIM_BYTES = 150L * 1024L * 1024L;

    /**
     * The preferences file osmdroid reads its own settings from.
     *
     * <p>osmdroid's documentation uses {@code android.preference.PreferenceManager}, which is
     * deprecated. Passing our own file avoids that without changing any behaviour - osmdroid simply
     * reads whatever preferences it is given.
     */
    private static final String OSMDROID_PREFS_NAME = "osmdroid_prefs";

    // ------------------------------------------------------------------ default camera

    /**
     * Default camera position: a Philippines-wide view.
     *
     * <p>The Phase 3.1 brief says to use a default camera position <i>only when necessary</i>, and this
     * is that case - on a cold start there is nothing else to look at yet. It is a <b>view default, not
     * data</b>: no store coordinate is invented here, and nothing is stored.
     *
     * <p>Phase 3.2 replaces it with the user's actual location as soon as a fix is available.
     */
    public static final double DEFAULT_CENTER_LATITUDE = 12.8797;
    public static final double DEFAULT_CENTER_LONGITUDE = 121.7740;
    public static final double DEFAULT_ZOOM = 5.5;

    /**
     * The zoom level used when the map centres on the user's position.
     *
     * <p>Close enough to see the surrounding streets - which is what matters when you are trying to work
     * out where you are relative to a store - but not so close that the accuracy circle fills the screen.
     */
    public static final double LOCATION_ZOOM = 16.0;

    // ------------------------------------------------------------------ setup

    /**
     * Applies the tile policy settings and the User-Agent, once, at application start.
     *
     * <p>Called from {@code AlphaRouteApp.onCreate} rather than from the fragment, because it must be
     * done before <i>any</i> {@code MapView} is constructed - osmdroid resolves its tile cache path
     * during configuration loading, and a map built before this runs can end up with nowhere to cache.
     * Doing it in the Application object makes that ordering guaranteed instead of hopeful.
     */
    public static void initialise(Context context) {
        // In osmdroid 6.1.x, Configuration is only a holder: getInstance() returns the
        // IConfigurationProvider interface, and every setter lives on that interface. Typing the
        // variable as Configuration would not compile, because the interface is not a Configuration.
        IConfigurationProvider config = Configuration.getInstance();

        config.load(context, context.getSharedPreferences(OSMDROID_PREFS_NAME, Context.MODE_PRIVATE));

        // The policy requirement that, if missed, makes the map silently stop working.
        config.setUserAgentValue(MapUserAgent.build(
                com.cph.alpharoute.BuildConfig.APPLICATION_ID,
                com.cph.alpharoute.BuildConfig.VERSION_NAME));

        config.setTileFileSystemCacheMaxBytes(TILE_CACHE_MAX_BYTES);
        config.setTileFileSystemCacheTrimBytes(TILE_CACHE_TRIM_BYTES);
    }

    /**
     * The tile source the map draws from.
     *
     * <p>Built explicitly rather than taken from {@code TileSourceFactory.MAPNIK} so that the exact
     * URL, the zoom range and the attribution are all stated here, in the open, and cannot change under
     * us when the library updates its defaults. osmdroid's factory remains available and other public
     * OSM-derived sources could be dropped in here instead.
     */
    public static XYTileSource tileSource() {
        return new XYTileSource(
                TILE_SOURCE_NAME,
                MIN_ZOOM,
                MAX_ZOOM,
                TILE_SIZE_PX,
                ".png",
                new String[]{OSM_TILE_BASE_URL},
                OSM_COPYRIGHT);
    }

    /**
     * The attribution text that must be visible on the map.
     *
     * <p>Kept here rather than in {@code strings.xml} on purpose: it is provider metadata that has to
     * match the tile source above, so the two are written next to each other and cannot drift. It is
     * the one deliberate exception to this project's "all user-visible text lives in strings.xml" rule.
     */
    public static String attribution() {
        return OSM_COPYRIGHT;
    }
}
