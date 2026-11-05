package com.gcu.trafficscotland.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gcu.trafficscotland.R;
import com.gcu.trafficscotland.TrafficApp;
import com.gcu.trafficscotland.data.DataOrigin;
import com.gcu.trafficscotland.data.TrafficRepository;
import com.gcu.trafficscotland.data.TrafficSnapshot;
import com.gcu.trafficscotland.logic.SituationQuery;
import com.gcu.trafficscotland.logic.SituationText;
import com.gcu.trafficscotland.map.OsmMapView;
import com.gcu.trafficscotland.model.FeedKind;
import com.gcu.trafficscotland.model.Situation;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Name                 _________________
 * Student ID           _________________
 * Programme of Study   _________________
 *
 * Portrait is a searchable list. Landscape is a different arrangement:
 * the list stays on the left and the map plus the selected situation stay on the right.
 */
public class MainActivity extends AppCompatActivity implements TrafficRepository.Listener, LocationListener {
    public static final double GLASGOW_LAT = 55.8609;
    public static final double GLASGOW_LON = -4.2518;
    public static final int[] RADII_KM = {5, 10, 25, 50, 100, 200};
    private static final int LOCATION_REQUEST = 42;

    private static final String STATE_FEED = "feed";
    private static final String STATE_DISTANCE = "distance";
    private static final String STATE_RADIUS = "radius";
    private static final String STATE_DEVICE = "device";
    private static final String STATE_LAT = "lat";
    private static final String STATE_LON = "lon";
    private static final String STATE_NAME = "name";
    private static final String STATE_ID = "id";
    private static final String STATE_ROAD = "road";

    private TrafficRepository repository;
    private SituationAdapter adapter;
    private TextView statusText;
    private TextView noticeBanner;
    private TextView emptyText;
    private TextView distanceValue;
    private TextView locationLabel;
    private TextView previewTitle;
    private TextView previewComment;
    private TextView previewMeta;
    private View previewButton;
    private ProgressBar progress;
    private RecyclerView list;
    private OsmMapView embeddedMap;
    private SwitchCompat distanceSwitch;
    private SeekBar distanceSeek;
    private MaterialButtonToggleGroup feedToggle;
    private android.widget.EditText roadSearch;
    private Button locationButton;

    private FeedKind feed;
    private boolean distanceOn;
    private int radiusIndex = 2;
    private boolean useDeviceLocation;
    private double latitude = GLASGOW_LAT;
    private double longitude = GLASGOW_LON;
    private String locationName;
    private String selectedId = "";
    private boolean refreshing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repository = ((TrafficApp) getApplication()).repository();
        locationName = getString(R.string.location_fixed);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusText = findViewById(R.id.statusText);
        noticeBanner = findViewById(R.id.noticeBanner);
        emptyText = findViewById(R.id.emptyText);
        distanceValue = findViewById(R.id.distanceValue);
        locationLabel = findViewById(R.id.locationLabel);
        progress = findViewById(R.id.progress);
        list = findViewById(R.id.list);
        roadSearch = findViewById(R.id.roadSearch);
        distanceSwitch = findViewById(R.id.distanceSwitch);
        distanceSeek = findViewById(R.id.distanceSeek);
        feedToggle = findViewById(R.id.feedToggle);
        locationButton = findViewById(R.id.locationButton);
        embeddedMap = findViewById(R.id.embeddedMap);
        previewTitle = findViewById(R.id.previewTitle);
        previewComment = findViewById(R.id.previewComment);
        previewMeta = findViewById(R.id.previewMeta);
        previewButton = findViewById(R.id.previewButton);

        adapter = new SituationAdapter(this::onSituationChosen);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        if (savedInstanceState != null) {
            restore(savedInstanceState);
        }
        distanceSeek.setMax(RADII_KM.length - 1);
        distanceSeek.setProgress(radiusIndex);
        distanceSwitch.setChecked(distanceOn);
        roadSearch.setText(savedInstanceState == null ? "" : savedInstanceState.getString(STATE_ROAD, ""));
        feedToggle.check(feedId());
        applyLocationControls();

        roadSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        feedToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.feedIncidents) {
                feed = FeedKind.INCIDENT;
            } else if (checkedId == R.id.feedRoadworks) {
                feed = FeedKind.ROADWORKS;
            } else {
                feed = null;
            }
            render();
        });
        distanceSwitch.setOnCheckedChangeListener((button, checked) -> {
            distanceOn = checked;
            distanceSeek.setEnabled(checked);
            render();
        });
        distanceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radiusIndex = progress;
                distanceValue.setText(getString(R.string.distance_km, RADII_KM[radiusIndex]));
                if (fromUser && distanceOn) {
                    render();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        locationButton.setOnClickListener(v -> {
            if (useDeviceLocation) {
                useFixedPoint();
            } else {
                requestDeviceLocation();
            }
        });
        if (previewButton != null) {
            previewButton.setOnClickListener(v -> openDetail(selectedId));
        }
        if (embeddedMap != null) {
            embeddedMap.setOnSituationClickListener(this::onSituationChosen);
            wireZoom(embeddedMap);
        }
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        repository.addListener(this);
    }

    @Override
    protected void onStop() {
        repository.removeListener(this);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FEED, feed == null ? -1 : feed.ordinal());
        outState.putBoolean(STATE_DISTANCE, distanceOn);
        outState.putInt(STATE_RADIUS, radiusIndex);
        outState.putBoolean(STATE_DEVICE, useDeviceLocation);
        outState.putDouble(STATE_LAT, latitude);
        outState.putDouble(STATE_LON, longitude);
        outState.putString(STATE_NAME, locationName);
        outState.putString(STATE_ID, selectedId);
        outState.putString(STATE_ROAD, roadSearch.getText().toString());
    }

    @Override
    public void onTrafficUpdated(TrafficSnapshot snapshot, boolean refreshing) {
        this.refreshing = refreshing;
        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem location = menu.findItem(R.id.action_location);
        if (location != null) {
            location.setTitle(useDeviceLocation ? R.string.use_glasgow : R.string.use_my_location);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            repository.refreshNow();
            return true;
        }
        if (id == R.id.action_map) {
            startActivity(MapActivity.intent(this, true, feed, roadSearch.getText().toString(),
                    distanceOn, latitude, longitude, RADII_KM[radiusIndex], selectedId));
            return true;
        }
        if (id == R.id.action_location) {
            if (useDeviceLocation) {
                useFixedPoint();
            } else {
                requestDeviceLocation();
            }
            return true;
        }
        if (id == R.id.action_about) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.about_title)
                    .setMessage(R.string.about_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestDeviceLocation();
            } else {
                useDeviceLocation = false;
                Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show();
                applyLocationControls();
            }
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        locationName = getString(R.string.location_phone);
        useDeviceLocation = true;
        applyLocationControls();
        render();
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager != null) {
            manager.removeUpdates(this);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
    }

    private void onSituationChosen(Situation situation) {
        selectedId = situation.stableId();
        if (previewTitle != null) {
            render();
            if (embeddedMap != null) {
                embeddedMap.centerOn(situation);
            }
            int position = adapter.positionOf(selectedId);
            if (position >= 0) {
                list.scrollToPosition(position);
            }
        } else {
            render();
            openDetail(selectedId);
        }
    }

    private void openDetail(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        startActivity(DetailActivity.intent(this, id));
    }

    private void render() {
        TrafficSnapshot snapshot = repository.snapshot();
        double radius = RADII_KM[Math.max(0, Math.min(RADII_KM.length - 1, radiusIndex))];
        SituationQuery query = new SituationQuery(
                feed,
                roadSearch.getText().toString(),
                distanceOn ? latitude : null,
                distanceOn ? longitude : null,
                distanceOn ? radius : null);
        List<Situation> shown = query.apply(snapshot.situations);
        Situation selected = null;
        for (Situation situation : shown) {
            if (situation.stableId().equals(selectedId)) {
                selected = situation;
                break;
            }
        }
        if (selected == null) {
            selectedId = "";
        }
        adapter.submit(shown, selectedId, distanceOn, latitude, longitude);
        boolean empty = shown.isEmpty();
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            if (snapshot.situations.isEmpty()) {
                emptyText.setText(refreshing ? R.string.loading : R.string.no_data);
            } else {
                emptyText.setText(R.string.no_matches);
            }
        }
        statusText.setText(status(snapshot, shown.size()));
        if (snapshot.notice == null || snapshot.notice.isEmpty()) {
            noticeBanner.setVisibility(View.GONE);
        } else {
            noticeBanner.setVisibility(View.VISIBLE);
            noticeBanner.setText(snapshot.notice);
            int background = R.color.notice_saved;
            if (snapshot.origin == DataOrigin.SAMPLE) {
                background = R.color.notice_sample;
            } else if (snapshot.origin == DataOrigin.EMPTY) {
                background = R.color.notice_error;
            }
            noticeBanner.setBackgroundColor(getColor(background));
        }
        progress.setVisibility(refreshing ? View.VISIBLE : View.GONE);
        distanceValue.setText(getString(R.string.distance_km, (int) radius));
        locationLabel.setText(getString(R.string.location_line, locationName, latitude, longitude));
        if (embeddedMap != null) {
            embeddedMap.setSituations(shown);
            embeddedMap.setSelectedId(selectedId);
        }
        showPreview(selected);
    }

    private void showPreview(Situation situation) {
        if (previewTitle == null) {
            return;
        }
        if (situation == null) {
            previewTitle.setText(R.string.preview_empty);
            previewComment.setText("");
            previewMeta.setText("");
            previewButton.setEnabled(false);
            return;
        }
        previewTitle.setText(SituationText.typeLabel(situation));
        previewComment.setText(SituationRows.comment(situation));
        previewMeta.setText(SituationRows.meta(situation, distanceOn, latitude, longitude));
        previewButton.setEnabled(true);
    }

    private String status(TrafficSnapshot snapshot, int shown) {
        String origin;
        switch (snapshot.origin) {
            case LIVE:
                origin = getString(R.string.origin_live);
                break;
            case SAVED:
                origin = getString(R.string.origin_saved);
                break;
            case SAMPLE:
                origin = getString(R.string.origin_sample);
                break;
            default:
                origin = getString(R.string.origin_waiting);
                break;
        }
        String line = getString(R.string.status_counts, shown, snapshot.situations.size(),
                snapshot.count(FeedKind.INCIDENT), snapshot.count(FeedKind.ROADWORKS),
                origin, TrafficRepository.REFRESH_MINUTES);
        StringBuilder extra = new StringBuilder();
        if (snapshot.incidentsPublished != null) {
            extra.append(getString(R.string.published_incidents, SituationText.formatWhen(snapshot.incidentsPublished)));
        }
        if (snapshot.roadworksPublished != null) {
            if (extra.length() > 0) {
                extra.append(" · ");
            }
            extra.append(getString(R.string.published_roadworks, SituationText.formatWhen(snapshot.roadworksPublished)));
        }
        if (snapshot.fetchedAtMillis > 0) {
            String updated = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK)
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(snapshot.fetchedAtMillis));
            if (extra.length() > 0) {
                extra.append(" · ");
            }
            extra.append(getString(R.string.updated, updated));
        }
        if (extra.length() == 0) {
            return line;
        }
        return line + "\n" + extra;
    }

    private void restore(Bundle state) {
        int feedOrdinal = state.getInt(STATE_FEED, -1);
        feed = feedOrdinal < 0 ? null : FeedKind.values()[feedOrdinal];
        distanceOn = state.getBoolean(STATE_DISTANCE);
        radiusIndex = state.getInt(STATE_RADIUS, 2);
        useDeviceLocation = state.getBoolean(STATE_DEVICE);
        latitude = state.getDouble(STATE_LAT, GLASGOW_LAT);
        longitude = state.getDouble(STATE_LON, GLASGOW_LON);
        locationName = state.getString(STATE_NAME, getString(R.string.location_fixed));
        selectedId = state.getString(STATE_ID, "");
    }

    private int feedId() {
        if (feed == FeedKind.INCIDENT) {
            return R.id.feedIncidents;
        }
        if (feed == FeedKind.ROADWORKS) {
            return R.id.feedRoadworks;
        }
        return R.id.feedAll;
    }

    private void applyLocationControls() {
        distanceSeek.setEnabled(distanceOn);
        locationButton.setText(useDeviceLocation ? R.string.use_glasgow : R.string.use_my_location);
        invalidateOptionsMenu();
    }

    private void useFixedPoint() {
        useDeviceLocation = false;
        latitude = GLASGOW_LAT;
        longitude = GLASGOW_LON;
        locationName = getString(R.string.location_fixed);
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager != null) {
            manager.removeUpdates(this);
        }
        applyLocationControls();
        render();
    }

    private void requestDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) {
            return;
        }
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        String provider = manager.getBestProvider(criteria, true);
        if (provider == null) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        useDeviceLocation = true;
        locationName = getString(R.string.location_waiting);
        applyLocationControls();
        Location last = manager.getLastKnownLocation(provider);
        if (last != null) {
            onLocationChanged(last);
        } else {
            manager.requestSingleUpdate(provider, this, Looper.getMainLooper());
            render();
        }
    }

    private void wireZoom(OsmMapView map) {
        View zoomIn = findViewById(R.id.zoomIn);
        View zoomOut = findViewById(R.id.zoomOut);
        View fit = findViewById(R.id.fitMap);
        if (zoomIn != null) {
            zoomIn.setOnClickListener(v -> map.zoomBy(1));
        }
        if (zoomOut != null) {
            zoomOut.setOnClickListener(v -> map.zoomBy(-1));
        }
        if (fit != null) {
            fit.setOnClickListener(v -> map.fitToSituations());
        }
    }
}
