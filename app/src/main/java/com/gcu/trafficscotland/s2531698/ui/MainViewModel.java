package com.gcu.trafficscotland.s2531698.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.gcu.trafficscotland.s2531698.TrafficApp;
import com.gcu.trafficscotland.s2531698.data.TrafficRepository;
import com.gcu.trafficscotland.s2531698.data.TrafficSnapshot;
import com.gcu.trafficscotland.s2531698.logic.SituationQuery;
import com.gcu.trafficscotland.s2531698.model.FeedKind;
import com.gcu.trafficscotland.s2531698.model.Situation;

import java.util.List;

/**
 * View model for the one activity. The model is {@link TrafficRepository} and the
 * parsed {@link Situation} records. {@link MainActivity}, {@link DetailFragment} and
 * {@link MapFragment} are the views: they observe {@link #state()} and send actions
 * here. This class does not hold a view, a context widget, or a layout.
 */
public class MainViewModel extends AndroidViewModel implements TrafficRepository.Listener {
    public static final double GLASGOW_LAT = 55.8609;
    public static final double GLASGOW_LON = -4.2518;
    public static final int[] RADII_KM = {5, 10, 25, 50, 100, 200};

    public enum LocationSource {
        FIXED,
        WAITING,
        PHONE
    }

    /** Current list, or every situation, when the map screen is opened. */
    public enum MapFilter {
        CURRENT,
        ALL
    }

    public enum Screen {
        DETAIL,
        MAP
    }

    /** One-shot navigation. Rotation replays the same object, which has already been consumed. */
    public static final class Nav {
        private final Screen screen;
        private boolean handled;

        Nav(Screen screen) {
            this.screen = screen;
        }

        Screen consume() {
            if (handled) {
                return null;
            }
            handled = true;
            return screen;
        }
    }

    /** Immutable picture of the screen. Views render this and do not rebuild the query. */
    public static final class State {
        public final TrafficSnapshot snapshot;
        public final boolean refreshing;
        public final FeedKind feed;
        public final String road;
        public final boolean distanceOn;
        public final int radiusIndex;
        public final int radiusKm;
        public final int refreshMinutes;
        public final LocationSource locationSource;
        public final double latitude;
        public final double longitude;
        public final String selectedId;
        public final List<Situation> shown;
        public final Situation selectedInList;
        public final MapFilter mapFilter;
        public final List<Situation> mapSituations;
        public final Situation selectedSituation;

        State(TrafficSnapshot snapshot, boolean refreshing, FeedKind feed, String road,
              boolean distanceOn, int radiusIndex, int radiusKm, LocationSource locationSource,
              double latitude, double longitude, String selectedId, List<Situation> shown,
              Situation selectedInList, MapFilter mapFilter, List<Situation> mapSituations,
              Situation selectedSituation) {
            this.snapshot = snapshot;
            this.refreshing = refreshing;
            this.feed = feed;
            this.road = road;
            this.distanceOn = distanceOn;
            this.radiusIndex = radiusIndex;
            this.radiusKm = radiusKm;
            this.refreshMinutes = TrafficRepository.REFRESH_MINUTES;
            this.locationSource = locationSource;
            this.latitude = latitude;
            this.longitude = longitude;
            this.selectedId = selectedId;
            this.shown = shown;
            this.selectedInList = selectedInList;
            this.mapFilter = mapFilter;
            this.mapSituations = mapSituations;
            this.selectedSituation = selectedSituation;
        }
    }

    private static final String KEY_FEED = "feed";
    private static final String KEY_ROAD = "road";
    private static final String KEY_DISTANCE = "distance";
    private static final String KEY_RADIUS = "radius";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_ID = "id";
    private static final String KEY_MAP = "map";

    private final SavedStateHandle saved;
    private final TrafficRepository repository;
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<Nav> navigation = new MutableLiveData<>();

    private TrafficSnapshot snapshot = TrafficSnapshot.empty();
    private boolean refreshing;
    private FeedKind feed;
    private String road = "";
    private boolean distanceOn;
    private int radiusIndex = 2;
    private LocationSource source = LocationSource.FIXED;
    private double latitude = GLASGOW_LAT;
    private double longitude = GLASGOW_LON;
    private String selectedId = "";
    private MapFilter mapFilter = MapFilter.CURRENT;

    public MainViewModel(@NonNull Application application, @NonNull SavedStateHandle saved) {
        super(application);
        this.saved = saved;
        restore();
        repository = ((TrafficApp) application).repository();
        repository.addListener(this);
    }

    public LiveData<State> state() {
        return state;
    }

    public LiveData<Nav> navigation() {
        return navigation;
    }

    public boolean usingDeviceLocation() {
        return source != LocationSource.FIXED;
    }

    public void setRoad(String next) {
        String value = next == null ? "" : next;
        if (value.equals(road)) {
            return;
        }
        road = value;
        remember();
        publish();
    }

    public void setFeed(FeedKind next) {
        if (next == feed) {
            return;
        }
        feed = next;
        remember();
        publish();
    }

    public void setDistanceEnabled(boolean enabled) {
        if (enabled == distanceOn) {
            return;
        }
        distanceOn = enabled;
        remember();
        publish();
    }

    public void setRadiusIndex(int index) {
        int next = Math.max(0, Math.min(RADII_KM.length - 1, index));
        if (next == radiusIndex) {
            return;
        }
        radiusIndex = next;
        remember();
        publish();
    }

    public void select(String id) {
        String next = id == null ? "" : id;
        if (next.equals(selectedId)) {
            return;
        }
        selectedId = next;
        remember();
        publish();
    }

    public void useFixedPoint() {
        source = LocationSource.FIXED;
        latitude = GLASGOW_LAT;
        longitude = GLASGOW_LON;
        remember();
        publish();
    }

    public void markWaitingForPhone() {
        source = LocationSource.WAITING;
        remember();
        publish();
    }

    public void usePhoneLocation(double lat, double lon) {
        source = LocationSource.PHONE;
        latitude = lat;
        longitude = lon;
        remember();
        publish();
    }

    public void refresh() {
        repository.refreshNow();
    }

    public void openDetail() {
        if (selectedId.isEmpty()) {
            return;
        }
        navigation.setValue(new Nav(Screen.DETAIL));
    }

    public void openMap(MapFilter filter) {
        mapFilter = filter == null ? MapFilter.CURRENT : filter;
        remember();
        publish();
        navigation.setValue(new Nav(Screen.MAP));
    }

    @Override
    public void onTrafficUpdated(TrafficSnapshot next, boolean nowRefreshing) {
        snapshot = next == null ? TrafficSnapshot.empty() : next;
        refreshing = nowRefreshing;
        publish();
    }

    @Override
    protected void onCleared() {
        repository.removeListener(this);
    }

    private void publish() {
        int radiusKm = RADII_KM[radiusIndex];
        SituationQuery query = new SituationQuery(
                feed,
                road,
                distanceOn ? latitude : null,
                distanceOn ? longitude : null,
                distanceOn ? (double) radiusKm : null);
        List<Situation> shown = query.apply(snapshot.situations);
        Situation selectedInList = null;
        for (Situation situation : shown) {
            if (situation.stableId().equals(selectedId)) {
                selectedInList = situation;
                break;
            }
        }
        List<Situation> mapSituations = mapFilter == MapFilter.ALL ? snapshot.situations : shown;
        state.setValue(new State(
                snapshot,
                refreshing,
                feed,
                road,
                distanceOn,
                radiusIndex,
                radiusKm,
                source,
                latitude,
                longitude,
                selectedId,
                shown,
                selectedInList,
                mapFilter,
                mapSituations,
                snapshot.find(selectedId)));
    }

    private void restore() {
        Integer feedOrdinal = saved.get(KEY_FEED);
        if (feedOrdinal != null && feedOrdinal >= 0 && feedOrdinal < FeedKind.values().length) {
            feed = FeedKind.values()[feedOrdinal];
        }
        String storedRoad = saved.get(KEY_ROAD);
        road = storedRoad == null ? "" : storedRoad;
        Boolean distance = saved.get(KEY_DISTANCE);
        distanceOn = distance != null && distance;
        Integer radius = saved.get(KEY_RADIUS);
        if (radius != null && radius >= 0 && radius < RADII_KM.length) {
            radiusIndex = radius;
        }
        String sourceName = saved.get(KEY_SOURCE);
        if (sourceName != null) {
            try {
                source = LocationSource.valueOf(sourceName);
            } catch (IllegalArgumentException ignored) {
                source = LocationSource.FIXED;
            }
        }
        Double lat = saved.get(KEY_LAT);
        Double lon = saved.get(KEY_LON);
        if (lat != null && lon != null) {
            latitude = lat;
            longitude = lon;
        }
        String id = saved.get(KEY_ID);
        selectedId = id == null ? "" : id;
        String mapName = saved.get(KEY_MAP);
        if (mapName != null) {
            try {
                mapFilter = MapFilter.valueOf(mapName);
            } catch (IllegalArgumentException ignored) {
                mapFilter = MapFilter.CURRENT;
            }
        }
    }

    private void remember() {
        saved.set(KEY_FEED, feed == null ? -1 : feed.ordinal());
        saved.set(KEY_ROAD, road);
        saved.set(KEY_DISTANCE, distanceOn);
        saved.set(KEY_RADIUS, radiusIndex);
        saved.set(KEY_SOURCE, source.name());
        saved.set(KEY_LAT, latitude);
        saved.set(KEY_LON, longitude);
        saved.set(KEY_ID, selectedId);
        saved.set(KEY_MAP, mapFilter.name());
    }
}
