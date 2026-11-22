package com.gcu.trafficscotland.s2531698.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gcu.trafficscotland.s2531698.BuildConfig;
import com.gcu.trafficscotland.s2531698.model.FeedKind;
import com.gcu.trafficscotland.s2531698.model.ParseResult;
import com.gcu.trafficscotland.s2531698.model.Situation;
import com.gcu.trafficscotland.s2531698.parser.DatexPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads both DATEX publications, parses them off the UI thread and keeps the last
 * good copy for when the network is unavailable.
 *
 * This is not the skeleton's single Task thread. A scheduler thread refreshes on
 * a timer, a download thread fetches the XML strings, and a parse thread runs
 * XmlPullParser on those strings. Results are published on the main thread.
 */
public class TrafficRepository {
    public interface Listener {
        void onTrafficUpdated(TrafficSnapshot snapshot, boolean refreshing);
    }

    public static final int REFRESH_MINUTES = 5;
    private static final String TAG = "TrafficRefresh";
    private static final String UNPLANNED_FILE = "unplanned.xml";
    private static final String ROADWORKS_FILE = "roadworks.xml";

    private final Context context;
    private final DatexDownloader downloader = new DatexDownloader();
    private final DatexPullParser parser = new DatexPullParser();
    private final ExecutorService downloads;
    private final ExecutorService parsing;
    private final ScheduledExecutorService scheduler;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean runAgain = new AtomicBoolean(false);

    private volatile TrafficSnapshot snapshot = TrafficSnapshot.empty();
    private volatile boolean refreshing;
    private boolean started;

    public TrafficRepository(Context context) {
        this.context = context.getApplicationContext();
        downloads = Executors.newFixedThreadPool(2, runnable -> named(runnable, "datex-download"));
        parsing = Executors.newSingleThreadExecutor(runnable -> named(runnable, "datex-parse"));
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> named(runnable, "datex-schedule"));
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        watchNetwork();
        scheduler.scheduleAtFixedRate(() -> refresh(false), 0, REFRESH_MINUTES, TimeUnit.MINUTES);
    }

    public void refreshNow() {
        refresh(true);
    }

    public TrafficSnapshot snapshot() {
        return snapshot;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        listener.onTrafficUpdated(snapshot, refreshing);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void refresh(boolean manual) {
        if (!running.compareAndSet(false, true)) {
            if (manual) {
                runAgain.set(true);
            }
            return;
        }
        refreshing = true;
        main.post(this::notifyListeners);
        downloads.execute(() -> {
            Loaded loaded = loadFeeds();
            parsing.execute(() -> publish(loaded));
        });
    }

    private void publish(Loaded loaded) {
        Log.i(TAG, "Parsing on " + Thread.currentThread().getName());
        List<Situation> situations = new ArrayList<>();
        String incidentsPublished = null;
        String roadworksPublished = null;
        String notice = loaded.notice;
        try {
            if (loaded.unplannedXml != null) {
                ParseResult parsed = parser.parse(loaded.unplannedXml, FeedKind.INCIDENT);
                situations.addAll(parsed.situations);
                incidentsPublished = parsed.publicationTime;
            }
        } catch (Exception ex) {
            Log.e(TAG, "Incident parse failed", ex);
            notice = join(notice, "The incidents feed could not be read.");
        }
        try {
            if (loaded.roadworksXml != null) {
                ParseResult parsed = parser.parse(loaded.roadworksXml, FeedKind.ROADWORKS);
                situations.addAll(parsed.situations);
                roadworksPublished = parsed.publicationTime;
            }
        } catch (Exception ex) {
            Log.e(TAG, "Roadworks parse failed", ex);
            notice = join(notice, "The roadworks feed could not be read.");
        }
        if (situations.isEmpty() && notice == null) {
            notice = "No situations were found in the feeds.";
        }
        Log.i(TAG, "Parsed " + situations.size() + " situations");
        TrafficSnapshot next = new TrafficSnapshot(
                situations,
                System.currentTimeMillis(),
                loaded.origin,
                notice,
                incidentsPublished,
                roadworksPublished);
        main.post(() -> {
            snapshot = next;
            refreshing = false;
            running.set(false);
            notifyListeners();
            if (runAgain.compareAndSet(true, false)) {
                refresh(false);
            }
        });
    }

    private Loaded loadFeeds() {
        Loaded loaded = new Loaded();
        if (!credentialsReady()) {
            loaded.unplannedXml = readCacheOrAsset(UNPLANNED_FILE, loaded);
            loaded.roadworksXml = readCacheOrAsset(ROADWORKS_FILE, loaded);
            loaded.origin = loaded.usedSaved ? DataOrigin.SAVED : DataOrigin.SAMPLE;
            loaded.notice = "DATEX client id and key are not in this build. "
                    + "Showing " + (loaded.usedSaved ? "the last saved copy." : "the coursework sample.")
                    + " Copy datex.properties.example to datex.properties, add your credentials and rebuild.";
            return loaded;
        }
        if (!isOnline()) {
            loaded.unplannedXml = readCacheOrAsset(UNPLANNED_FILE, loaded);
            loaded.roadworksXml = readCacheOrAsset(ROADWORKS_FILE, loaded);
            loaded.origin = loaded.usedSaved ? DataOrigin.SAVED : DataOrigin.SAMPLE;
            loaded.notice = "No internet connection. Showing "
                    + (loaded.usedSaved ? "the most recent saved data." : "the coursework sample, because nothing has been saved yet.");
            return loaded;
        }
        loaded.unplannedXml = downloadOrFallback(DatexDownloader.UNPLANNED_URL, UNPLANNED_FILE, loaded, true);
        loaded.roadworksXml = downloadOrFallback(DatexDownloader.ROADWORKS_URL, ROADWORKS_FILE, loaded, false);
        if (loaded.liveCount == 2) {
            loaded.origin = DataOrigin.LIVE;
        } else if (loaded.liveCount > 0 && loaded.usedSaved) {
            loaded.origin = DataOrigin.SAVED;
        } else if (loaded.liveCount > 0) {
            loaded.origin = DataOrigin.LIVE;
        } else if (loaded.usedSaved) {
            loaded.origin = DataOrigin.SAVED;
        } else {
            loaded.origin = DataOrigin.EMPTY;
        }
        return loaded;
    }

    private String downloadOrFallback(String url, String file, Loaded loaded, boolean unplanned) {
        try {
            Log.i(TAG, "Downloading on " + Thread.currentThread().getName());
            String body = downloader.download(url, BuildConfig.DATEX_CLIENT_ID, BuildConfig.DATEX_CLIENT_KEY);
            writeCache(file, body);
            loaded.liveCount++;
            return body;
        } catch (IOException ex) {
            Log.w(TAG, "Download failed: " + ex.getMessage());
            String cached = readCache(file);
            String which = unplanned ? "Incidents" : "Roadworks";
            if (cached != null) {
                loaded.usedSaved = true;
                loaded.notice = join(loaded.notice, which + " could not be downloaded ("
                        + ex.getMessage() + "). Showing the last saved copy.");
                return cached;
            }
            loaded.notice = join(loaded.notice, which + " could not be downloaded ("
                    + ex.getMessage() + "). No saved copy is available.");
            return null;
        }
    }

    private String readCacheOrAsset(String file, Loaded loaded) {
        String cached = readCache(file);
        if (cached != null) {
            loaded.usedSaved = true;
            return cached;
        }
        return readAsset(file);
    }

    private void watchNetwork() {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return;
        }
        manager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                refresh(false);
            }

            @Override
            public void onLost(Network network) {
                main.post(() -> {
                    if (snapshot.origin != DataOrigin.EMPTY) {
                        snapshot = snapshot.withNotice(
                                "Internet connection lost. Showing the most recent data. "
                                        + "It will refresh when the connection returns.");
                        notifyListeners();
                    }
                });
            }
        });
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private boolean credentialsReady() {
        return present(BuildConfig.DATEX_CLIENT_ID) && present(BuildConfig.DATEX_CLIENT_KEY);
    }

    private static boolean present(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return !trimmed.isEmpty()
                && !trimmed.contains("YOUR_ID")
                && !trimmed.contains("YOUR_KEY");
    }

    private void writeCache(String name, String xml) {
        File destination = new File(context.getFilesDir(), name);
        File temporary = new File(context.getFilesDir(), name + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            Log.w(TAG, "Could not save " + name);
            return;
        }
        if (destination.exists() && !destination.delete()) {
            Log.w(TAG, "Could not replace " + name);
        }
        if (!temporary.renameTo(destination)) {
            Log.w(TAG, "Could not move saved " + name);
        }
    }

    private String readCache(String name) {
        File file = new File(context.getFilesDir(), name);
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            return readStream(input);
        } catch (IOException ex) {
            return null;
        }
    }

    private String readAsset(String name) {
        try (InputStream input = context.getAssets().open(name)) {
            return readStream(input);
        } catch (IOException ex) {
            return null;
        }
    }

    private static String readStream(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private void notifyListeners() {
        for (Listener listener : listeners) {
            listener.onTrafficUpdated(snapshot, refreshing);
        }
    }

    private static String join(String current, String extra) {
        if (current == null || current.isEmpty()) {
            return extra;
        }
        return current + " " + extra;
    }

    private static Thread named(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static final class Loaded {
        private String unplannedXml;
        private String roadworksXml;
        private DataOrigin origin = DataOrigin.EMPTY;
        private String notice;
        private boolean usedSaved;
        private int liveCount;
    }
}
