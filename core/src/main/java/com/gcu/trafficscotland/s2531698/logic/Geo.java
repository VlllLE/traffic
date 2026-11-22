package com.gcu.trafficscotland.s2531698.logic;

import com.gcu.trafficscotland.s2531698.model.GeoPoint;
import com.gcu.trafficscotland.s2531698.model.Situation;

/** Distances used by the "situations near me" search. */
public final class Geo {
    private static final double EARTH_KM = 6371.0;

    private Geo() {
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Distance from a point to a situation. A linear location uses the closest
     * point on the from-to segment. Missing coordinates return positive infinity
     * so a distance search does not pretend the situation is nearby.
     */
    public static double distanceToSituationKm(double latitude, double longitude, Situation situation) {
        if (situation == null) {
            return Double.POSITIVE_INFINITY;
        }
        GeoPoint from = situation.from;
        GeoPoint to = situation.to;
        if (from.hasCoordinates && to.hasCoordinates) {
            return distanceToSegmentKm(latitude, longitude,
                    from.latitude, from.longitude, to.latitude, to.longitude);
        }
        if (from.hasCoordinates) {
            return haversineKm(latitude, longitude, from.latitude, from.longitude);
        }
        if (to.hasCoordinates) {
            return haversineKm(latitude, longitude, to.latitude, to.longitude);
        }
        return Double.POSITIVE_INFINITY;
    }

    public static double distanceToSegmentKm(double lat, double lon,
                                             double lat1, double lon1,
                                             double lat2, double lon2) {
        double meanLat = Math.toRadians((lat + lat1 + lat2) / 3.0);
        double kx = 111.32 * Math.cos(meanLat);
        double ky = 110.57;
        double x = lon * kx;
        double y = lat * ky;
        double x1 = lon1 * kx;
        double y1 = lat1 * ky;
        double x2 = lon2 * kx;
        double y2 = lat2 * ky;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : ((x - x1) * dx + (y - y1) * dy) / lengthSquared;
        if (t < 0) {
            t = 0;
        } else if (t > 1) {
            t = 1;
        }
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        return Math.hypot(x - closestX, y - closestY);
    }
}
