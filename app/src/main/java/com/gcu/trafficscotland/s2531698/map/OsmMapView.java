package com.gcu.trafficscotland.s2531698.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.gcu.trafficscotland.s2531698.logic.SituationText;
import com.gcu.trafficscotland.s2531698.logic.WebMercator;
import com.gcu.trafficscotland.s2531698.model.GeoPoint;
import com.gcu.trafficscotland.s2531698.model.Situation;
import com.gcu.trafficscotland.s2531698.ui.DelayColours;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pan and zoom map drawn from OpenStreetMap tiles, with a line for each
 * linear situation and a marker at each end. Tiles are optional: if they
 * cannot be fetched the markers are still drawn on a grid.
 */
public class OsmMapView extends View {
    public interface SituationClickListener {
        void onSituationClick(Situation situation);
    }

    private static final int MIN_ZOOM = 5;
    private static final int MAX_ZOOM = 12;
    private static final String USER_AGENT = "GCU-MPD-TrafficScotland/1.0 (educational coursework)";

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaleGestures;
    private final LruCache<String, Bitmap> memory = new LruCache<>(64);
    private final Set<String> requested = ConcurrentHashMap.newKeySet();
    private final ExecutorService tiles = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "osm-tile");
        thread.setDaemon(true);
        return thread;
    });
    private final File cacheDir;
    private final List<Situation> situations = new ArrayList<>();

    private int zoom = 6;
    private double centerX;
    private double centerY;
    private boolean userMoved;
    private boolean pendingFit = true;
    private float pinch = 1f;
    private String selectedId = "";
    private SituationClickListener clickListener;

    public OsmMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        cacheDir = new File(context.getCacheDir(), "osm");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            // The map still works from memory if the disk cache cannot be created.
        }
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(1f);
        grid.setColor(0x330B3A4A);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(0xFFFFFFFF);
        label.setColor(0xFF102027);
        label.setTextSize(dp(12));
        labelBg.setColor(0xEEFFFFFF);
        setCenter(57.0, -4.5);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                userMoved = true;
                centerX += distanceX;
                centerY += distanceY;
                clampCenter();
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Situation hit = hitTest(e.getX(), e.getY());
                if (hit != null && clickListener != null) {
                    selectedId = hit.stableId();
                    invalidate();
                    clickListener.onSituationClick(hit);
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                zoomBy(1);
                return true;
            }
        });
        scaleGestures = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                pinch *= detector.getScaleFactor();
                if (pinch > 1.3f) {
                    zoomBy(1);
                    pinch = 1f;
                } else if (pinch < 0.75f) {
                    zoomBy(-1);
                    pinch = 1f;
                }
                return true;
            }
        });
    }

    public void setOnSituationClickListener(SituationClickListener listener) {
        this.clickListener = listener;
    }

    public void setSituations(List<Situation> next) {
        situations.clear();
        if (next != null) {
            situations.addAll(next);
        }
        if (!userMoved) {
            pendingFit = true;
        }
        invalidate();
    }

    public void setSelectedId(String id) {
        selectedId = id == null ? "" : id;
        invalidate();
    }

    public void centerOn(Situation situation) {
        if (situation == null || !situation.hasAnyCoordinates()) {
            return;
        }
        userMoved = true;
        pendingFit = false;
        double latitude;
        double longitude;
        if (situation.hasLine()) {
            latitude = (situation.from.latitude + situation.to.latitude) / 2.0;
            longitude = (situation.from.longitude + situation.to.longitude) / 2.0;
        } else if (situation.from.hasCoordinates) {
            latitude = situation.from.latitude;
            longitude = situation.from.longitude;
        } else {
            latitude = situation.to.latitude;
            longitude = situation.to.longitude;
        }
        setCenter(latitude, longitude);
        invalidate();
    }

    public void zoomBy(int delta) {
        int next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + delta));
        if (next == zoom) {
            return;
        }
        double latitude = WebMercator.latitude(centerY, zoom);
        double longitude = WebMercator.longitude(centerX, zoom);
        zoom = next;
        userMoved = true;
        setCenter(latitude, longitude);
        invalidate();
    }

    public void fitToSituations() {
        userMoved = false;
        pendingFit = true;
        if (getWidth() > 0) {
            fitNow();
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (pendingFit && !userMoved && w > 0 && h > 0) {
            fitNow();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pendingFit && !userMoved && getWidth() > 0) {
            fitNow();
        }
        canvas.drawColor(0xFFD7E6F2);
        double left = centerX - getWidth() / 2.0;
        double top = centerY - getHeight() / 2.0;
        drawTiles(canvas, left, top);
        for (Situation situation : situations) {
            drawSituation(canvas, situation, situation.stableId().equals(selectedId));
        }
        Situation selected = selected();
        if (selected != null) {
            drawLabel(canvas, selected);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean scale = scaleGestures.onTouchEvent(event);
        boolean gesture = gestures.onTouchEvent(event);
        return scale || gesture || true;
    }

    private void drawTiles(Canvas canvas, double left, double top) {
        int limit = 1 << zoom;
        int x0 = (int) Math.floor(left / WebMercator.TILE_SIZE);
        int y0 = (int) Math.floor(top / WebMercator.TILE_SIZE);
        int x1 = (int) Math.floor((left + getWidth()) / WebMercator.TILE_SIZE);
        int y1 = (int) Math.floor((top + getHeight()) / WebMercator.TILE_SIZE);
        for (int tileX = x0; tileX <= x1; tileX++) {
            for (int tileY = y0; tileY <= y1; tileY++) {
                if (tileX < 0 || tileY < 0 || tileX >= limit || tileY >= limit) {
                    continue;
                }
                float screenX = (float) (tileX * WebMercator.TILE_SIZE - left);
                float screenY = (float) (tileY * WebMercator.TILE_SIZE - top);
                Bitmap bitmap = tile(zoom, tileX, tileY);
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, screenX, screenY, null);
                }
                canvas.drawRect(screenX, screenY, screenX + WebMercator.TILE_SIZE, screenY + WebMercator.TILE_SIZE, grid);
            }
        }
    }

    private void drawSituation(Canvas canvas, Situation situation, boolean selected) {
        int colour = DelayColours.stripe(getContext(), situation);
        fill.setColor(colour);
        Float x1 = screenX(situation.from);
        Float y1 = screenY(situation.from);
        Float x2 = screenX(situation.to);
        Float y2 = screenY(situation.to);
        if (x1 != null && y1 != null && x2 != null && y2 != null) {
            stroke.setColor(colour);
            stroke.setStrokeWidth(selected ? dp(5) : dp(3));
            canvas.drawLine(x1, y1, x2, y2, stroke);
        }
        float radius = selected ? dp(9) : dp(6);
        if (x1 != null && y1 != null) {
            drawMarker(canvas, x1, y1, radius);
        }
        if (x2 != null && y2 != null) {
            drawMarker(canvas, x2, y2, radius);
        }
    }

    private void drawMarker(Canvas canvas, float x, float y, float radius) {
        stroke.setColor(0xFFFFFFFF);
        stroke.setStrokeWidth(dp(2));
        canvas.drawCircle(x, y, radius, fill);
        canvas.drawCircle(x, y, radius, stroke);
    }

    private void drawLabel(Canvas canvas, Situation situation) {
        String text = SituationText.typeLabel(situation);
        String roads = SituationText.roadsLabel(situation);
        if (!roads.isEmpty()) {
            text = roads + " · " + text;
        }
        if (text.length() > 42) {
            text = text.substring(0, 41) + "…";
        }
        float width = label.measureText(text);
        Float anchorX = screenX(situation.hasLine() ? midpoint(situation, true) : firstPoint(situation));
        Float anchorY = screenY(situation.hasLine() ? midpoint(situation, false) : firstPoint(situation));
        if (anchorX == null || anchorY == null) {
            return;
        }
        float left = Math.max(dp(4), Math.min(getWidth() - width - dp(16), anchorX + dp(10)));
        float top = Math.max(dp(4), anchorY - dp(28));
        RectF box = new RectF(left, top, left + width + dp(12), top + dp(22));
        canvas.drawRoundRect(box, dp(4), dp(4), labelBg);
        canvas.drawText(text, left + dp(6), top + dp(15), label);
    }

    private GeoPoint midpoint(Situation situation, boolean unused) {
        GeoPoint point = new GeoPoint();
        point.latitude = (situation.from.latitude + situation.to.latitude) / 2.0;
        point.longitude = (situation.from.longitude + situation.to.longitude) / 2.0;
        point.hasCoordinates = true;
        point.sawLatitude = true;
        point.sawLongitude = true;
        return point;
    }

    private GeoPoint firstPoint(Situation situation) {
        if (situation.from.hasCoordinates) {
            return situation.from;
        }
        return situation.to;
    }

    private Situation selected() {
        for (Situation situation : situations) {
            if (situation.stableId().equals(selectedId)) {
                return situation;
            }
        }
        return null;
    }

    private Situation hitTest(float x, float y) {
        Situation best = null;
        double bestDistance = dp(28);
        for (Situation situation : situations) {
            double distance = distanceToSituation(situation, x, y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = situation;
            }
        }
        return best;
    }

    private double distanceToSituation(Situation situation, float x, float y) {
        Float x1 = screenX(situation.from);
        Float y1 = screenY(situation.from);
        Float x2 = screenX(situation.to);
        Float y2 = screenY(situation.to);
        double best = Double.POSITIVE_INFINITY;
        if (x1 != null && y1 != null) {
            best = Math.hypot(x - x1, y - y1);
        }
        if (x2 != null && y2 != null) {
            best = Math.min(best, Math.hypot(x - x2, y - y2));
        }
        if (x1 != null && y1 != null && x2 != null && y2 != null) {
            best = Math.min(best, distanceToSegment(x, y, x1, y1, x2, y2));
        }
        return best;
    }

    private static double distanceToSegment(float x, float y, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = dx * dx + dy * dy;
        float t = length == 0 ? 0 : ((x - x1) * dx + (y - y1) * dy) / length;
        if (t < 0) {
            t = 0;
        } else if (t > 1) {
            t = 1;
        }
        return Math.hypot(x - (x1 + t * dx), y - (y1 + t * dy));
    }

    private Float screenX(GeoPoint point) {
        if (point == null || !point.hasCoordinates) {
            return null;
        }
        return (float) (WebMercator.worldX(point.longitude, zoom) - (centerX - getWidth() / 2.0));
    }

    private Float screenY(GeoPoint point) {
        if (point == null || !point.hasCoordinates) {
            return null;
        }
        return (float) (WebMercator.worldY(point.latitude, zoom) - (centerY - getHeight() / 2.0));
    }

    private Bitmap tile(int z, int x, int y) {
        String key = z + "/" + x + "/" + y;
        Bitmap cached = memory.get(key);
        if (cached != null) {
            return cached;
        }
        if (requested.add(key)) {
            tiles.execute(() -> loadTile(z, x, y, key));
        }
        return null;
    }

    private void loadTile(int z, int x, int y, String key) {
        File disk = new File(cacheDir, z + "_" + x + "_" + y + ".png");
        Bitmap bitmap = null;
        if (disk.exists()) {
            bitmap = BitmapFactory.decodeFile(disk.getAbsolutePath());
        }
        if (bitmap == null) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://tile.openstreetmap.org/" + z + "/" + x + "/" + y + ".png");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStream input = connection.getInputStream()) {
                        bitmap = BitmapFactory.decodeStream(input);
                    }
                    if (bitmap != null) {
                        try (FileOutputStream output = new FileOutputStream(disk)) {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                        } catch (Exception ignored) {
                            // A missing disk copy only means the next visit refetches the tile.
                        }
                    }
                }
            } catch (Exception ignored) {
                // Leave the key in the requested set so a failure is not retried on every frame.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        if (bitmap != null) {
            memory.put(key, bitmap);
            postInvalidate();
        }
    }

    private void fitNow() {
        pendingFit = false;
        List<double[]> points = new ArrayList<>();
        for (Situation situation : situations) {
            addPoint(points, situation.from);
            addPoint(points, situation.to);
        }
        if (points.isEmpty()) {
            zoom = 6;
            setCenter(57.0, -4.5);
            return;
        }
        double minLat = 90;
        double maxLat = -90;
        double minLon = 180;
        double maxLon = -180;
        for (double[] point : points) {
            minLat = Math.min(minLat, point[0]);
            maxLat = Math.max(maxLat, point[0]);
            minLon = Math.min(minLon, point[1]);
            maxLon = Math.max(maxLon, point[1]);
        }
        if (points.size() == 1 || (maxLat - minLat < 0.01 && maxLon - minLon < 0.01)) {
            zoom = 11;
            setCenter((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0);
            return;
        }
        int chosen = MIN_ZOOM;
        for (int candidate = MAX_ZOOM; candidate >= MIN_ZOOM; candidate--) {
            double width = WebMercator.worldX(maxLon, candidate) - WebMercator.worldX(minLon, candidate);
            double height = WebMercator.worldY(minLat, candidate) - WebMercator.worldY(maxLat, candidate);
            if (width * 1.35 < getWidth() && height * 1.35 < getHeight()) {
                chosen = candidate;
                break;
            }
        }
        zoom = chosen;
        setCenter((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0);
    }

    private static void addPoint(List<double[]> points, GeoPoint point) {
        if (point != null && point.hasCoordinates) {
            points.add(new double[]{point.latitude, point.longitude});
        }
    }

    private void setCenter(double latitude, double longitude) {
        centerX = WebMercator.worldX(longitude, zoom);
        centerY = WebMercator.worldY(latitude, zoom);
        clampCenter();
    }

    private void clampCenter() {
        double world = (double) (1 << zoom) * WebMercator.TILE_SIZE;
        centerX = Math.max(0, Math.min(world, centerX));
        centerY = Math.max(0, Math.min(world, centerY));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
