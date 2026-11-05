package com.gcu.trafficscotland.logic;

/** Web Mercator helpers shared by the map view and its tests. */
public final class WebMercator {
    public static final int TILE_SIZE = 256;
    private static final double MAX_LAT = 85.05112878;

    private WebMercator() {
    }

    public static double worldX(double longitude, int zoom) {
        double scale = (double) (1 << zoom) * TILE_SIZE;
        return (longitude + 180.0) / 360.0 * scale;
    }

    public static double worldY(double latitude, int zoom) {
        double clamped = Math.max(-MAX_LAT, Math.min(MAX_LAT, latitude));
        double latRad = Math.toRadians(clamped);
        double mercator = Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad));
        double scale = (double) (1 << zoom) * TILE_SIZE;
        return (1.0 - mercator / Math.PI) / 2.0 * scale;
    }

    public static double longitude(double worldX, int zoom) {
        double scale = (double) (1 << zoom) * TILE_SIZE;
        return worldX / scale * 360.0 - 180.0;
    }

    public static double latitude(double worldY, int zoom) {
        double scale = (double) (1 << zoom) * TILE_SIZE;
        double mercator = Math.PI * (1.0 - 2.0 * worldY / scale);
        return Math.toDegrees(Math.atan(Math.sinh(mercator)));
    }

    public static int tileX(double longitude, int zoom) {
        return clampTile((int) Math.floor(worldX(longitude, zoom) / TILE_SIZE), zoom);
    }

    public static int tileY(double latitude, int zoom) {
        return clampTile((int) Math.floor(worldY(latitude, zoom) / TILE_SIZE), zoom);
    }

    private static int clampTile(int tile, int zoom) {
        int max = (1 << zoom) - 1;
        if (tile < 0) {
            return 0;
        }
        return Math.min(tile, max);
    }
}
