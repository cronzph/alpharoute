package com.cph.alpharoute.ui.map;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.cph.alpharoute.R;
import com.cph.alpharoute.data.db.StoreEntity;
import com.cph.alpharoute.domain.GeoCoordinate;
import com.cph.alpharoute.domain.StoreStatus;
import com.cph.alpharoute.util.GeoConstants;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the store markers on the map.
 *
 * <p>This is the only class in the app that touches osmdroid's {@code Marker} type, which is the point:
 * the map library stays behind one boundary, so the tracker, the database and the completion rules never
 * depend on it. Architecture decision AD-8.
 *
 * <p><b>Every marker is keyed by store id, and carries that id on itself.</b> That association is what
 * lets a tap resolve back to the right store - the controller translates the osmdroid event into a plain
 * store id and hands it to an {@link OnStoreClickListener} - and it is also what stops a marker and its
 * store drifting apart when the list changes. {@link Marker#setRelatedObject(Object)} holds it.
 *
 * <p><b>One entry point: {@link #renderAll}.</b> The map hands over the whole current list of stores and
 * this class reconciles the markers against it - adding, moving, recolouring and removing as needed. It
 * is deliberately not a set of "add one" and "remove one" calls: with a full list to compare against,
 * a deleted store cannot be left behind on the map by a missed callback, and a restart needs no special
 * case at all. Phase 3.4's "marker persistence after restart" is simply what happens when the first
 * emission of the list arrives.
 */
public class MapMarkerController {

    /**
     * Told when the user taps a store marker.
     *
     * <p>An interface rather than a direct call into the map screen, because this class is the only one
     * allowed to touch osmdroid's {@code Marker} (AD-8). The fragment that wants to know about taps
     * therefore cannot register an osmdroid listener itself without breaking that boundary - so the
     * controller takes the listener and translates osmdroid's event into a plain store id. What comes
     * out is {@code long storeId}, with no osmdroid type anywhere in it.
     */
    public interface OnStoreClickListener {

        /**
         * @param storeId the id of the store whose marker was tapped - never a position, so the caller
         *                cannot accidentally act on the wrong store if the list has since changed
         */
        void onStoreClicked(long storeId);
    }

    private final MapView mapView;
    private final Map<Long, MarkerEntry> markersById = new HashMap<>();

    /**
     * Home's pin, or {@code null} when there is no Home to draw.
     *
     * <p>Deliberately kept out of {@code markersById}. Home is not a store, and {@link #markerCount()} is
     * the number Phase 6.5 compares against the store count in the database - so counting Home there
     * would make that consistency check report a permanent, false discrepancy.
     */
    @Nullable
    private Marker homeMarker;

    /**
     * The route line, or {@code null} when no route is being shown.
     *
     * <p>Like Home, it is kept out of {@code markersById}: it is not a store, and {@link #markerCount()}
     * has to stay a count of stores for Phase 6.5's consistency check.
     */
    @Nullable
    private Polyline routeLine;

    /** How thick the route line is drawn. */
    private static final float ROUTE_WIDTH_DP = 6f;

    /** Breathing room left around the route when the camera fits it to the screen. */
    private static final int ROUTE_ZOOM_PADDING_DP = 48;

    /**
     * Who to tell about a tap. {@code null} until the map wires one up.
     *
     * <p>Read from this field at tap time rather than captured when the marker is created, so a listener
     * set after the markers were drawn still receives their taps.
     */
    @Nullable
    private OnStoreClickListener storeClickListener;

    public MapMarkerController(@NonNull MapView mapView) {
        this.mapView = mapView;
    }

    /**
     * Sets who is told when a store marker is tapped. Passing {@code null} switches tapping off, which
     * is what the map does when its view is destroyed.
     */
    public void setOnStoreClickListener(@Nullable OnStoreClickListener listener) {
        this.storeClickListener = listener;
    }

    /**
     * Makes the map's markers match the given stores.
     *
     * <p>Reconciles rather than rebuilds: existing markers are moved and updated in place, so panning and
     * zooming are not disturbed when the database changes - which happens on every completion.
     *
     * @param stores every store in the database. Pass an empty list to clear the map.
     */
    public void renderAll(@NonNull List<StoreEntity> stores) {
        Set<Long> incoming = new HashSet<>(stores.size());

        for (StoreEntity store : stores) {
            // A store with an impossible coordinate gets no marker at all, and any marker it used to
            // have is pruned below. Drawing it would put a pin somewhere the user never chose.
            if (!hasPlottableCoordinates(store)) {
                continue;
            }
            incoming.add(store.id);
            upsert(store);
        }

        prune(incoming);
        mapView.invalidate();
    }

    /** Removes everything this controller owns - the stores, Home, and the route line. */
    public void clear() {
        for (MarkerEntry entry : markersById.values()) {
            mapView.getOverlays().remove(entry.marker);
        }
        markersById.clear();

        // Home goes with them. It is not in markersById, but it is still an overlay on this MapView and
        // would otherwise be left behind in a list that is about to be discarded with the view.
        removeHomeMarker();

        // And so does the route line, for the same reason.
        clearRoute();

        mapView.invalidate();
    }

    /**
     * How many <b>store</b> markers are currently on the map.
     *
     * <p>Phase 6.5's consistency check compares this against the store count in the database, which is
     * how a marker that failed to draw would be noticed at all. <b>Home is not included</b>, and must
     * never be: Home is not a store, so counting it would make every run of that check report a
     * discrepancy of one.
     */
    public int markerCount() {
        return markersById.size();
    }

    // ------------------------------------------------------------------ home

    /**
     * Draws the Home pin, or removes it when there is no Home to draw.
     *
     * <p><b>Home is never a store and never affects a count.</b> It has its own pin here, outside
     * {@code markersById}, so {@link #markerCount()} stays a count of stores.
     *
     * <p>Two screens call this, and they mean the same thing by it:
     *
     * <ul>
     *   <li>{@code MapFragment} passes the saved Home from the settings row - {@code null} when Home is
     *       not set, which removes the pin.</li>
     *   <li>{@code HomeSetupFragment} passes the point the user is currently considering. It <i>is</i>
     *       the Home pin being placed, so drawing it with the Home pin is exactly right.</li>
     * </ul>
     *
     * @param latitude  the Home latitude, or {@code null} to remove the pin
     * @param longitude the Home longitude, or {@code null} to remove the pin
     */
    public void renderHome(@Nullable Double latitude, @Nullable Double longitude) {
        if (!hasPlottableCoordinates(latitude, longitude)) {
            removeHomeMarker();
            return;
        }

        // The check above guarantees neither is null. Naming them keeps that explicit rather than relying
        // on the unboxing inside the GeoPoint constructor.
        double lat = latitude;
        double lng = longitude;

        if (homeMarker == null) {
            homeMarker = new Marker(mapView);

            // A house sits ON a place rather than pointing at one, so its base goes on the coordinate.
            homeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            homeMarker.setIcon(
                    ContextCompat.getDrawable(mapView.getContext(), R.drawable.ic_marker_home));
            homeMarker.setTitle(mapView.getContext().getString(R.string.marker_home_title));

            // Transparent to taps, and that is deliberate. Returning false lets osmdroid keep walking down
            // the overlay stack instead of showing its own info-window bubble - DefaultOverlayManager
            // iterates overlaysReversed() and continues while an overlay returns false. So on the Map tab
            // tapping Home does nothing, and in Home Setup a tap on the pin still places the pin, which is
            // exactly what you want when you are nudging it into position.
            homeMarker.setOnMarkerClickListener((tapped, map) -> false);

            // Added last, so it draws above the long-press overlay that MapFragment puts at index 0. It
            // consumes nothing (see above); this is only about what is drawn on top.
            mapView.getOverlays().add(homeMarker);
        }

        homeMarker.setPosition(new GeoPoint(lat, lng));
        mapView.invalidate();
    }

    /** Removes the Home pin if there is one. Called when Home is cleared, and from {@link #clear()}. */
    private void removeHomeMarker() {
        if (homeMarker == null) {
            return;
        }
        mapView.getOverlays().remove(homeMarker);
        homeMarker = null;
        mapView.invalidate();
    }

    // ------------------------------------------------------------------ the route line

    /**
     * Draws the drive to a store, and moves the camera so the whole of it is on screen.
     *
     * <p>Called from the Map tab, which observes the routing repository. The preview sheet asks for the
     * route; this draws it. Neither knows about the other, which is what keeps the map out of the
     * preview's business.
     *
     * <p><b>The line goes in at index 0, the bottom of the overlay stack.</b> osmdroid walks overlays
     * from the top down, so this puts the route underneath the store markers and underneath the tap
     * handlers. That matters: the line is decoration, and a marker sitting on top of it must still be
     * tappable. A {@code Polyline} consumes no taps on its own - it overrides neither
     * {@code onSingleTapConfirmed} nor {@code onLongPress}, and no click listener is set here.
     *
     * @param points the line, in order. Fewer than two cannot be drawn, and removes any existing line.
     */
    public void renderRoute(@NonNull List<GeoCoordinate> points) {
        if (points.size() < 2) {
            clearRoute();
            return;
        }

        List<GeoPoint> geoPoints = new ArrayList<>(points.size());
        for (GeoCoordinate point : points) {
            geoPoints.add(new GeoPoint(point.latitude(), point.longitude()));
        }

        if (routeLine == null) {
            routeLine = new Polyline(mapView);
            mapView.getOverlays().add(0, routeLine);
        }

        routeLine.setPoints(geoPoints);
        routeLine.setColor(ContextCompat.getColor(mapView.getContext(), R.color.route_line));

        // getOutlinePaint() rather than setWidth(): setWidth is deprecated in osmdroid 6.1.x and both
        // write the same paint, so the deprecated one would only produce a warning.
        routeLine.getOutlinePaint().setStrokeWidth(
                ROUTE_WIDTH_DP * mapView.getContext().getResources().getDisplayMetrics().density);

        mapView.invalidate();
        fitCameraToRoute(geoPoints);
    }

    /** Removes the route line if there is one. */
    public void clearRoute() {
        if (routeLine == null) {
            return;
        }
        mapView.getOverlays().remove(routeLine);
        routeLine = null;
        mapView.invalidate();
    }

    /**
     * Moves the camera so the whole route is visible.
     *
     * <p>The camera is otherwise never moved for the user - the Map tab centres once on the first fix
     * and after that only when they ask. This is them asking: they tapped Get Directions, and a route
     * that runs off the edge of the screen answers nothing.
     */
    private void fitCameraToRoute(@NonNull List<GeoPoint> points) {
        BoundingBox box = BoundingBox.fromGeoPoints(points);
        int paddingPixels = Math.round(
                ROUTE_ZOOM_PADDING_DP * mapView.getContext().getResources().getDisplayMetrics().density);
        mapView.zoomToBoundingBox(box, true, paddingPixels);
    }

    // ------------------------------------------------------------------ reconciliation

    /** Creates the marker if it is new, then applies the store's current position and status to it. */
    private void upsert(@NonNull StoreEntity store) {
        MarkerEntry entry = markersById.get(store.id);

        if (entry == null) {
            Marker marker = new Marker(mapView);

            // A teardrop points at a place rather than sitting on one, so the anchor goes at the bottom
            // centre - otherwise the tip of the pin would hover above the true coordinate.
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            // The marker carries its store id, so a tap can be resolved back to a store unambiguously.
            marker.setRelatedObject(store.id);

            // Tapping a marker opens the Store Details sheet (Phase 3.5).
            //
            // Without a listener, osmdroid's default behaviour is to show its own info-window bubble
            // (Marker.onMarkerClickDefault), which would sit on top of the map competing with our sheet.
            // Returning true marks the tap as consumed, so the bubble never appears.
            //
            // The id is read back off the marker rather than captured from the loop variable, and that is
            // the point of setRelatedObject above: if the association were ever lost, the tap falls
            // through instead of opening the wrong store. A wrong store silently shown is far worse than
            // a tap that does nothing.
            marker.setOnMarkerClickListener((tapped, map) -> {
                Object related = tapped.getRelatedObject();
                if (storeClickListener != null && related instanceof Long) {
                    storeClickListener.onStoreClicked((Long) related);
                    return true;
                }
                return false;
            });

            entry = new MarkerEntry(marker);
            markersById.put(store.id, entry);
            mapView.getOverlays().add(marker);
        }

        // The icon follows the status, and is re-applied only when the status actually changed. A store
        // flipping from pending to completed turns green without rebuilding every other marker, and
        // without allocating a fresh Drawable for markers that did not change.
        if (entry.renderedStatus != store.status) {
            entry.marker.setIcon(
                    ContextCompat.getDrawable(mapView.getContext(), markerIconRes(store.status)));
            entry.renderedStatus = store.status;
        }

        entry.marker.setPosition(new GeoPoint(store.latitude, store.longitude));
        entry.marker.setTitle(store.storeName);
        entry.marker.setSnippet(store.storeCode);
    }

    /** Removes markers whose store is no longer in the list - a deleted store must not linger. */
    private void prune(@NonNull Set<Long> incoming) {
        Iterator<Map.Entry<Long, MarkerEntry>> iterator = markersById.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, MarkerEntry> entry = iterator.next();
            if (!incoming.contains(entry.getKey())) {
                mapView.getOverlays().remove(entry.getValue().marker);
                iterator.remove();
            }
        }
    }

    /**
     * Whether a store can be drawn at all.
     *
     * <p>Delegates to the coordinate rule below rather than repeating the bounds, so a store and Home can
     * never be judged by two different standards.
     */
    private static boolean hasPlottableCoordinates(@NonNull StoreEntity store) {
        return GeoConstants.isValidLatitude(store.latitude)
                && GeoConstants.isValidLongitude(store.longitude);
    }

    /**
     * Whether a coordinate can be drawn at all - a null, a NaN and an out-of-globe value are all refused.
     *
     * <p>Package-private and static so {@code MapMarkerControllerTest} can prove it, and that is not
     * ceremony: drawing a bad coordinate puts a pin somewhere the user never chose. Latitude 0 is in the
     * Atlantic and NaN has no position at all, so either would look like a real store that had simply
     * been pinned in the wrong place - the kind of wrong that is easy to believe.
     *
     * <p>The bounds come from {@link GeoConstants}, the same constants {@code StoreValidator} and
     * {@code StoreRepository} use, so "a real coordinate" has one definition in this app rather than two
     * that can drift apart.
     *
     * <p>NaN needs no separate check: it fails both comparisons, so {@code isValidLatitude} already
     * returns false for it.
     */
    static boolean hasPlottableCoordinates(@Nullable Double latitude, @Nullable Double longitude) {
        return latitude != null && longitude != null
                && GeoConstants.isValidLatitude(latitude)
                && GeoConstants.isValidLongitude(longitude);
    }

    // ------------------------------------------------------------------ appearance

    /**
     * The icon for a store's status.
     *
     * <p>Package-private rather than private so that {@code MapMarkerControllerTest} can assert the two
     * statuses never resolve to the same drawable. That is not a cosmetic check: orange and green are the
     * classic red/green-confusion pair, and the whole accessibility argument rests on the shapes being
     * different - a filled circle against a check mark.
     */
    @DrawableRes
    static int markerIconRes(@NonNull StoreStatus status) {
        return status == StoreStatus.COMPLETED
                ? R.drawable.ic_marker_completed
                : R.drawable.ic_marker_pending;
    }

    /**
     * A marker together with the status it is currently drawn as.
     *
     * <p>The status is remembered so the icon is only replaced when it actually needs to be. Without it,
     * every database change would re-decode both drawables for every marker on the map.
     */
    private static final class MarkerEntry {

        final Marker marker;

        /** {@code null} until an icon has been applied. */
        StoreStatus renderedStatus;

        MarkerEntry(@NonNull Marker marker) {
            this.marker = marker;
        }
    }
}
