package com.cph.alpharoute.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.GoogleMapsLinkParser;
import com.cph.alpharoute.domain.PlaceLinkState;
import com.cph.alpharoute.util.AppExecutors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Turns a pasted Google Maps link into a location.
 *
 * <p>Two very different jobs, and it is worth being clear which is which:
 *
 * <ul>
 *   <li><b>A full link, or a bare coordinate pair, needs no network at all.</b>
 *       {@link GoogleMapsLinkParser} reads it on the spot. That is the whole of the tested path.</li>
 *   <li><b>A short link has to be followed.</b> {@code maps.app.goo.gl/…} carries no coordinates - it is a
 *       redirect, and the coordinates are at the other end of it. That needs a request, which is why this
 *       lives in the data layer rather than in the parser.</li>
 * </ul>
 *
 * <p><b>Following a short link takes three steps, because one is not enough.</b> A short link may answer
 * with a redirect whose {@code Location} header already holds the coordinates; it may redirect to
 * something that redirects again; or it may answer with a page that redirects in JavaScript, in which case
 * there is no {@code Location} header at all and the coordinates are only in the body. So each hop is
 * parsed, and the final body is read as a last resort. Every step runs the same tested parser over
 * whatever text it found.
 *
 * <p><b>The state is {@link LiveData}, like {@code RoutingRepository} and {@code LocationProvider}.</b>
 * That is what lets the sheet that collects the link and the Map tab that has to move the camera to the
 * result observe the same thing without a reference to each other - and it means a rotation during the
 * request does not lose it.
 *
 * <p><b>Nothing here invents a location.</b> If none of that produces a coordinate, the state says so and
 * the screen asks the user to paste something else. Pinning a store in the wrong place is worse than
 * asking again - and with a 50 m arrival radius, a pin a few hundred metres out means the app never offers
 * to complete the store, and never explains why.
 */
public class PlaceLinkRepository {

    private static final String TAG = "AlphaRoutePlaceLinkRepo";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    /** A short link's own redirect target, which is where the coordinates usually are. */
    private static final String LOCATION_HEADER = "Location";

    /** How many redirects to follow by hand before giving up. Real chains are one or two hops. */
    private static final int MAX_REDIRECTS = 3;

    /** How much of a page to read looking for coordinates. Enough for the head of a maps page, not the whole of it. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android) AlphaRoute/1.0";

    private final AppExecutors executors;
    private final MutableLiveData<PlaceLinkState> state = new MutableLiveData<>(PlaceLinkState.idle());

    public PlaceLinkRepository(@NonNull AppExecutors executors) {
        this.executors = executors;
    }

    /**
     * The current paste, observed.
     *
     * <p>Starts at {@link PlaceLinkState.Status#IDLE} and returns to it when {@link #clear()} is called.
     */
    public LiveData<PlaceLinkState> observe() {
        return state;
    }

    /**
     * Reads a location out of whatever the user pasted.
     *
     * @param text a link, a share blob, or a bare {@code "lat, lng"}. May be {@code null}.
     */
    public void resolve(@Nullable String text) {
        // A full link or bare coordinates are answered immediately, with no network involved.
        GeoCoordinate direct = GoogleMapsLinkParser.parse(text);
        if (direct != null) {
            publish(PlaceLinkState.found(direct));
            return;
        }

        if (!GoogleMapsLinkParser.isShortLink(text)) {
            // Not a link we understand, and not coordinates. There is nothing to follow.
            publish(PlaceLinkState.failed(PlaceLinkState.Status.NOT_A_LINK));
            return;
        }

        publish(PlaceLinkState.resolving());

        final String shortLink = text == null ? "" : text.trim();
        executors.networkIO().execute(() -> {
            final PlaceLinkState result = follow(shortLink);
            executors.postToMain(() -> state.setValue(result));
        });
    }

    /** Drops the current result and returns to {@link PlaceLinkState.Status#IDLE}. */
    public void clear() {
        publish(PlaceLinkState.idle());
    }

    /**
     * Points the map at a coordinate that did not come from a link.
     *
     * <p><b>Why this lives here.</b> The channel this class owns is really "a coordinate the Map tab
     * should fly to"; the link parsing is only one way to fill it. Phase 6.4's "View on Map" is another,
     * and reusing the channel means the Map tab needs no new observer and no second rule about when to
     * move the camera. The alternative - a second {@code LiveData} with its own observer in
     * {@code MapFragment} - would be two places deciding the same thing.
     *
     * <p>The published state is {@link PlaceLinkState#found}, so the Map tab treats it exactly as it
     * treats a resolved link: centre, and zoom in only if the camera is still wide.
     */
    public void showCoordinate(@NonNull GeoCoordinate coordinate) {
        publish(PlaceLinkState.found(coordinate));
    }

    // ------------------------------------------------------------------ following a short link

    private PlaceLinkState follow(@NonNull String shortLink) {
        String target = shortLink;

        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(target).openConnection();

                // Followed by hand, because what is wanted is the Location header - not the page it points
                // at. Letting the library follow would download a Google Maps page on every hop.
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);

                int code = connection.getResponseCode();
                String location = connection.getHeaderField(LOCATION_HEADER);

                if (location != null && !location.trim().isEmpty()) {
                    GeoCoordinate fromHeader = GoogleMapsLinkParser.parse(location);
                    if (fromHeader != null) {
                        return PlaceLinkState.found(fromHeader);
                    }
                    // A redirect with no coordinates in it - follow it and look again.
                    target = location.trim();
                    continue;
                }

                // No redirect: either the final page, or a page that redirects in JavaScript. Either way
                // the coordinates, if there are any, are in the body.
                GeoCoordinate fromBody =
                        GoogleMapsLinkParser.parse(readBody(connection.getInputStream()));
                if (fromBody != null) {
                    return PlaceLinkState.found(fromBody);
                }

                Log.w(TAG, "A short link resolved to nothing readable (HTTP " + code + ")");
                return PlaceLinkState.failed(PlaceLinkState.Status.UNRESOLVED);

            } catch (IOException e) {
                Log.w(TAG, "Could not follow the short link", e);
                return PlaceLinkState.failed(PlaceLinkState.Status.OFFLINE);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        // Redirected more times than a real link ever does. Rather than loop, say so.
        Log.w(TAG, "The short link redirected more than " + MAX_REDIRECTS + " times");
        return PlaceLinkState.failed(PlaceLinkState.Status.UNRESOLVED);
    }

    /**
     * Reads a bounded amount of a response body as text.
     *
     * <p>Bounded on purpose: a Google Maps page is far larger than anything needed to find a coordinate,
     * and reading all of it would spend the user's data to no purpose.
     */
    private static String readBody(@Nullable InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while (buffer.size() < MAX_BODY_BYTES && (read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Publishes a state on the main thread, which is where LiveData is allowed to be set from. */
    private void publish(@NonNull PlaceLinkState next) {
        state.setValue(next);
    }
}
