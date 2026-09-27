package com.cph.alpharoute.ui.map;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.cph.alpharoute.R;
import com.cph.alpharoute.location.LocationFix;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the user's current position: a blue dot with an accuracy circle around it.
 *
 * <p><b>Why this exists instead of osmdroid's {@code MyLocationNewOverlay}.</b> That overlay runs its
 * own location provider, which would give the app two independent streams of position - one for the
 * dot the user sees, another for the arrival check in Phase 5.1. They would disagree, and a store could
 * be completed from a fix the user was never shown. Everything here is driven by the same
 * {@link LocationFix} the rest of the app uses.
 *
 * <p><b>The accuracy circle is the honest part.</b> A GPS reading is not a point, it is a circle of
 * probability, and this draws it. A user indoors sees a large circle and understands why the dot is
 * wandering; a user with approximate-only permission sees the same thing. Without it, a coarse fix
 * looks exactly like a precise one - which is how a store gets counted from the wrong side of the road.
 *
 * <p>When a fix carries no accuracy value at all, the circle is hidden rather than drawn at some
 * invented radius. Showing nothing is honest; showing a made-up circle would not be.
 */
public class CurrentLocationOverlay {

    /** Enough points for a smooth circle at any zoom. Beyond this the extra vertices are invisible. */
    private static final int ACCURACY_SEGMENTS = 48;

    /** Metres per degree of latitude. Close enough at any latitude - this is a drawing, not a survey. */
    private static final double METRES_PER_DEGREE_LATITUDE = 111_320.0;

    private final MapView mapView;
    private final Marker dot;
    private final Polygon accuracyCircle;

    public CurrentLocationOverlay(MapView mapView) {
        this.mapView = mapView;

        // The dot. The anchor is moved to the centre, because a location dot marks a point rather than
        // standing on one - the default bottom-centre anchor would put the icon above the true position.
        dot = new Marker(mapView);
        dot.setIcon(ContextCompat.getDrawable(mapView.getContext(), R.drawable.ic_marker_current));
        dot.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        dot.setVisible(false);
        dot.setTitle(null);

        Context context = mapView.getContext();

        accuracyCircle = new Polygon(mapView);
        accuracyCircle.setFillColor(ContextCompat.getColor(context, R.color.map_accuracy_fill));
        accuracyCircle.setStrokeColor(ContextCompat.getColor(context, R.color.map_accuracy_stroke));
        accuracyCircle.setStrokeWidth(1f);
        accuracyCircle.setVisible(false);

        // The circle goes on first so the dot is drawn on top of it.
        mapView.getOverlays().add(accuracyCircle);
        mapView.getOverlays().add(dot);
    }

    /**
     * Moves the dot to the fix and resizes the accuracy circle.
     *
     * <p>Ignores a fix whose coordinates are outside the globe - a reading like that is broken, and
     * drawing it would put the dot somewhere impossible.
     */
    public void render(LocationFix fix) {
        if (fix == null || !fix.hasUsableCoordinates()) {
            return;
        }

        GeoPoint point = new GeoPoint(fix.latitude(), fix.longitude());
        dot.setPosition(point);
        dot.setVisible(true);

        if (fix.hasAccuracy()) {
            accuracyCircle.setPoints(circlePoints(fix.latitude(), fix.longitude(), fix.accuracyMetres()));
            accuracyCircle.setVisible(true);
        } else {
            // No accuracy value means no honest circle to draw. Hide it rather than invent a radius.
            accuracyCircle.setVisible(false);
        }

        mapView.invalidate();
    }

    /** Hides the dot and the circle - used when permission is lost or the screen is torn down. */
    public void clear() {
        dot.setVisible(false);
        accuracyCircle.setVisible(false);
        mapView.invalidate();
    }

    /** The last drawn position, or {@code null} if the dot has never been placed. */
    public GeoPoint lastPoint() {
        return dot.getPosition();
    }

    /**
     * Approximates a circle of the given radius around a point.
     *
     * <p>A flat-earth approximation, which is entirely adequate for an accuracy circle a few metres to a
     * few hundred metres across. The longitude scale shrinks towards the poles, so it is clamped - at
     * the pole itself the true scale is zero, and dividing by it would produce infinite coordinates.
     */
    private static List<GeoPoint> circlePoints(double latitude, double longitude, double radiusMetres) {
        double metresPerDegreeLongitude =
                METRES_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(latitude));
        if (Math.abs(metresPerDegreeLongitude) < 1.0) {
            metresPerDegreeLongitude = 1.0;
        }

        List<GeoPoint> points = new ArrayList<>(ACCURACY_SEGMENTS + 1);
        for (int i = 0; i <= ACCURACY_SEGMENTS; i++) {
            double angle = 2.0 * Math.PI * i / ACCURACY_SEGMENTS;
            double deltaLatitude = (radiusMetres * Math.cos(angle)) / METRES_PER_DEGREE_LATITUDE;
            double deltaLongitude = (radiusMetres * Math.sin(angle)) / metresPerDegreeLongitude;
            points.add(new GeoPoint(latitude + deltaLatitude, longitude + deltaLongitude));
        }
        return points;
    }
}
