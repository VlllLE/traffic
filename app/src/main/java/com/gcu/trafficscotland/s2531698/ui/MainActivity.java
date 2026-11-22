package com.gcu.trafficscotland.s2531698.ui;

import android.Manifest;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gcu.trafficscotland.s2531698.R;
import com.gcu.trafficscotland.s2531698.data.DataOrigin;
import com.gcu.trafficscotland.s2531698.data.TrafficSnapshot;
import com.gcu.trafficscotland.s2531698.logic.SituationText;
import com.gcu.trafficscotland.s2531698.map.OsmMapView;
import com.gcu.trafficscotland.s2531698.model.FeedKind;
import com.gcu.trafficscotland.s2531698.model.Situation;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

 /* Name - Matthew Jackson
 Student ID  - S2531698
 Programme - BSc Software Development
 */
public class MainActivity extends AppCompatActivity implements LocationListener {
        private static final int LOCATION_REQUEST = 42;

    private MainViewModel model;
    private SituationAdapter adapter;
    private View home;
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
    private boolean updating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        model = new ViewModelProvider(this).get(MainViewModel.class);
        home = findViewById(R.id.home);
        Toolbar toolbar = home.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusText = home.findViewById(R.id.statusText);
        noticeBanner = home.findViewById(R.id.noticeBanner);
        emptyText = home.findViewById(R.id.emptyText);
        distanceValue = home.findViewById(R.id.distanceValue);
        locationLabel = home.findViewById(R.id.locationLabel);
        progress = home.findViewById(R.id.progress);
        list = home.findViewById(R.id.list);
        roadSearch = home.findViewById(R.id.roadSearch);
        distanceSwitch = home.findViewById(R.id.distanceSwitch);
        distanceSeek = home.findViewById(R.id.distanceSeek);
        feedToggle = home.findViewById(R.id.feedToggle);
        locationButton = home.findViewById(R.id.locationButton);
        embeddedMap = home.findViewById(R.id.embeddedMap);
        previewTitle = home.findViewById(R.id.previewTitle);
        previewComment = home.findViewById(R.id.previewComment);
        previewMeta = home.findViewById(R.id.previewMeta);
        previewButton = home.findViewById(R.id.previewButton);

        adapter = new SituationAdapter(this::onSituationChosen);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        wireInput();
        if (embeddedMap != null) {
            embeddedMap.setOnSituationClickListener(this::onSituationChosen);
            wireZoom(embeddedMap);
        }
        if (previewButton != null) {
            previewButton.setOnClickListener(v -> model.openDetail());
        }
        getSupportFragmentManager().addOnBackStackChangedListener(this::syncScreen);
        syncScreen();
        model.state().observe(this, this::render);
        model.navigation().observe(this, nav -> {
            if (nav == null) {
                return;
            }
            MainViewModel.Screen screen = nav.consume();
            if (screen == MainViewModel.Screen.DETAIL) {
                showFragment(new DetailFragment());
            } else if (screen == MainViewModel.Screen.MAP) {
                showFragment(new MapFragment());
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        syncScreen();
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
            location.setTitle(model.usingDeviceLocation() ? R.string.use_glasgow : R.string.use_my_location);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            model.refresh();
            return true;
        }
        if (id == R.id.action_map) {
            model.openMap(MainViewModel.MapFilter.CURRENT);
            return true;
        }
        if (id == R.id.action_location) {
            if (model.usingDeviceLocation()) {
                stopLocationUpdates();
                model.useFixedPoint();
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
                Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        model.usePhoneLocation(location.getLatitude(), location.getLongitude());
        stopLocationUpdates();
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

    private void wireInput() {
        roadSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!updating) {
                    model.setRoad(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        feedToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || updating) {
                return;
            }
            if (checkedId == R.id.feedIncidents) {
                model.setFeed(FeedKind.INCIDENT);
            } else if (checkedId == R.id.feedRoadworks) {
                model.setFeed(FeedKind.ROADWORKS);
            } else {
                model.setFeed(null);
            }
        });
        distanceSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!updating) {
                model.setDistanceEnabled(checked);
            }
        });
        distanceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !updating) {
                    model.setRadiusIndex(progress);
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
            if (model.usingDeviceLocation()) {
                stopLocationUpdates();
                model.useFixedPoint();
            } else {
                requestDeviceLocation();
            }
        });
    }

    private void onSituationChosen(Situation situation) {
        model.select(situation.stableId());
        if (embeddedMap != null) {
            embeddedMap.centerOn(situation);
            int position = adapter.positionOf(situation.stableId());
            if (position >= 0) {
                list.scrollToPosition(position);
            }
        } else {
            model.openDetail();
        }
    }

    private void render(MainViewModel.State state) {
        updating = true;
        if (!roadSearch.getText().toString().equals(state.road)) {
            roadSearch.setText(state.road);
            roadSearch.setSelection(state.road.length());
        }
        feedToggle.check(feedId(state.feed));
        distanceSwitch.setChecked(state.distanceOn);
        distanceSeek.setEnabled(state.distanceOn);
        distanceSeek.setProgress(state.radiusIndex);
        updating = false;

        String selectedId = state.selectedInList == null ? "" : state.selectedId;
        adapter.submit(state.shown, selectedId, state.distanceOn, state.latitude, state.longitude);
        boolean empty = state.shown.isEmpty();
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            if (state.snapshot.situations.isEmpty()) {
                emptyText.setText(state.refreshing ? R.string.loading : R.string.no_data);
            } else {
                emptyText.setText(R.string.no_matches);
            }
        }
        statusText.setText(status(state));
        showNotice(state.snapshot);
        progress.setVisibility(state.refreshing ? View.VISIBLE : View.GONE);
        distanceValue.setText(getString(R.string.distance_km, state.radiusKm));
        locationLabel.setText(getString(R.string.location_line, locationName(state), state.latitude, state.longitude));
        locationButton.setText(state.locationSource == MainViewModel.LocationSource.FIXED
                ? R.string.use_my_location : R.string.use_glasgow);
        if (embeddedMap != null) {
            embeddedMap.setSituations(state.shown);
            embeddedMap.setSelectedId(selectedId);
        }
        showPreview(state);
        invalidateOptionsMenu();
    }

    private void showPreview(MainViewModel.State state) {
        if (previewTitle == null) {
            return;
        }
        Situation situation = state.selectedInList;
        if (situation == null) {
            previewTitle.setText(R.string.preview_empty);
            previewComment.setText("");
            previewMeta.setText("");
            previewButton.setEnabled(false);
            return;
        }
        previewTitle.setText(SituationText.typeLabel(situation));
        previewComment.setText(SituationRows.comment(situation));
        previewMeta.setText(SituationRows.meta(situation, state.distanceOn, state.latitude, state.longitude));
        previewButton.setEnabled(true);
    }

    private void showNotice(TrafficSnapshot snapshot) {
        if (snapshot.notice == null || snapshot.notice.isEmpty()) {
            noticeBanner.setVisibility(View.GONE);
            return;
        }
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

    private String status(MainViewModel.State state) {
        TrafficSnapshot snapshot = state.snapshot;
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
        String line = getString(R.string.status_counts, state.shown.size(), snapshot.situations.size(),
                snapshot.count(FeedKind.INCIDENT), snapshot.count(FeedKind.ROADWORKS),
                origin, state.refreshMinutes);
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

    private String locationName(MainViewModel.State state) {
        switch (state.locationSource) {
            case PHONE:
                return getString(R.string.location_phone);
            case WAITING:
                return getString(R.string.location_waiting);
            case FIXED:
            default:
                return getString(R.string.location_fixed);
        }
    }

    private int feedId(FeedKind feed) {
        if (feed == FeedKind.INCIDENT) {
            return R.id.feedIncidents;
        }
        if (feed == FeedKind.ROADWORKS) {
            return R.id.feedRoadworks;
        }
        return R.id.feedAll;
    }

    private void showFragment(Fragment fragment) {
        findViewById(R.id.screen).setVisibility(View.VISIBLE);
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.screen, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void syncScreen() {
        boolean open = getSupportFragmentManager().getBackStackEntryCount() > 0;
        findViewById(R.id.screen).setVisibility(open ? View.VISIBLE : View.GONE);
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
        model.markWaitingForPhone();
        Location last = manager.getLastKnownLocation(provider);
        if (last != null) {
            onLocationChanged(last);
        } else {
            manager.requestSingleUpdate(provider, this, Looper.getMainLooper());
        }
    }

    private void stopLocationUpdates() {
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager != null) {
            manager.removeUpdates(this);
        }
    }

    private void wireZoom(OsmMapView map) {
        View zoomIn = home.findViewById(R.id.zoomIn);
        View zoomOut = home.findViewById(R.id.zoomOut);
        View fit = home.findViewById(R.id.fitMap);
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
