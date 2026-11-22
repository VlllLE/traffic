package com.gcu.trafficscotland.s2531698.model;

import java.util.Locale;

/** One end of a DATEX location, or a single point when the feed has no from/to. */
public class GeoPoint {
    public double latitude;
    public double longitude;
    public boolean sawLatitude;
    public boolean sawLongitude;
    public boolean hasCoordinates;
    public String junction;
    public String place;

    public String label() {
        if (place != null && !place.isEmpty()) {
            return place;
        }
        if (junction != null && !junction.isEmpty()) {
            return junction;
        }
        if (hasCoordinates) {
            return String.format(Locale.UK, "%.5f, %.5f", latitude, longitude);
        }
        return "";
    }
}
