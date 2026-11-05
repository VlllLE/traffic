package com.gcu.trafficscotland.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.gcu.trafficscotland.R;
import com.gcu.trafficscotland.TrafficApp;
import com.gcu.trafficscotland.data.TrafficRepository;
import com.gcu.trafficscotland.data.TrafficSnapshot;
import com.gcu.trafficscotland.logic.SituationQuery;
import com.gcu.trafficscotland.logic.SituationText;
import com.gcu.trafficscotland.map.OsmMapView;
import com.gcu.trafficscotland.model.FeedKind;
import com.gcu.trafficscotland.model.Situation;

import java.util.List;

/**
 * Full-screen map. Portrait stacks the selection under the map.
 * Landscape puts the selection beside the map, which is a different arrangement.
 */
public class MapActivity extends AppCompatActivity implements TrafficRepository.Listener {
    private static final String EXTRA_FILTERED = "filtered";
    private static final String EXTRA_FEED = "feed";
    private static final String EXTRA_ROAD = "road";
    private static final String EXTRA_DISTANCE = "distance";
    private static final String EXTRA_LAT = "lat";
    private static final String EXTRA_LON = "lon";
    private static final String EXTRA_RADIUS = "radius";
    private static final String EXTRA_ID = "situationId";

    private TrafficRepository repository;
    private OsmMapView map;
    private TextView mapTitle;
    private TextView mapComment;
    private TextView mapMeta;
    private View mapButton;
    private boolean filtered;
    private FeedKind feed;
    private String road = "";
    private boolean distanceOn;
    private double latitude;
    private double longitude;
    private int radiusKm = 25;
    private String selectedId = "";
    private boolean centered;

    public static Intent intent(Context context, boolean filtered, FeedKind feed, String road,
                                boolean distanceOn, double latitude, double longitude,
                                int radiusKm, String situationId) {
        return new Intent(context, MapActivity.class)
                .putExtra(EXTRA_FILTERED, filtered)
                .putExtra(EXTRA_FEED, feed == null ? -1 : feed.ordinal())
                .putExtra(EXTRA_ROAD, road == null ? "" : road)
                .putExtra(EXTRA_DISTANCE, distanceOn)
                .putExtra(EXTRA_LAT, latitude)
                .putExtra(EXTRA_LON, longitude)
                .putExtra(EXTRA_RADIUS, radiusKm)
                .putExtra(EXTRA_ID, situationId == null ? "" : situationId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.map);
        }
        repository = ((TrafficApp) getApplication()).repository();
        Intent intent = getIntent();
        filtered = intent.getBooleanExtra(EXTRA_FILTERED, false);
        int feedOrdinal = intent.getIntExtra(EXTRA_FEED, -1);
        feed = feedOrdinal < 0 ? null : FeedKind.values()[feedOrdinal];
        road = intent.getStringExtra(EXTRA_ROAD);
        distanceOn = intent.getBooleanExtra(EXTRA_DISTANCE, false);
        latitude = intent.getDoubleExtra(EXTRA_LAT, MainActivity.GLASGOW_LAT);
        longitude = intent.getDoubleExtra(EXTRA_LON, MainActivity.GLASGOW_LON);
        radiusKm = intent.getIntExtra(EXTRA_RADIUS, 25);
        selectedId = intent.getStringExtra(EXTRA_ID);
        if (selectedId == null) {
            selectedId = "";
        }
        map = findViewById(R.id.map);
        mapTitle = findViewById(R.id.mapTitle);
        mapComment = findViewById(R.id.mapComment);
        mapMeta = findViewById(R.id.mapMeta);
        mapButton = findViewById(R.id.mapButton);
        map.setOnSituationClickListener(situation -> {
            selectedId = situation.stableId();
            map.setSelectedId(selectedId);
            showSelected(situation);
        });
        mapButton.setOnClickListener(v -> {
            if (!selectedId.isEmpty()) {
                startActivity(DetailActivity.intent(this, selectedId));
            }
        });
        findViewById(R.id.zoomIn).setOnClickListener(v -> map.zoomBy(1));
        findViewById(R.id.zoomOut).setOnClickListener(v -> map.zoomBy(-1));
        findViewById(R.id.fitMap).setOnClickListener(v -> map.fitToSituations());
        if (savedInstanceState != null) {
            centered = true;
        }
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
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onTrafficUpdated(TrafficSnapshot snapshot, boolean refreshing) {
        SituationQuery query = new SituationQuery(
                filtered ? feed : null,
                filtered ? road : "",
                filtered && distanceOn ? latitude : null,
                filtered && distanceOn ? longitude : null,
                filtered && distanceOn ? (double) radiusKm : null);
        List<Situation> shown = query.apply(snapshot.situations);
        map.setSituations(shown);
        map.setSelectedId(selectedId);
        Situation selected = null;
        for (Situation situation : shown) {
            if (situation.stableId().equals(selectedId)) {
                selected = situation;
                break;
            }
        }
        if (selected == null) {
            selected = snapshot.find(selectedId);
        }
        showSelected(selected);
        if (!centered && selected != null) {
            map.centerOn(selected);
            centered = true;
        }
    }

    private void showSelected(Situation situation) {
        if (situation == null) {
            mapTitle.setText(R.string.map_hint);
            mapComment.setText("");
            mapMeta.setText("");
            mapButton.setEnabled(false);
            return;
        }
        mapTitle.setText(SituationText.typeLabel(situation));
        mapComment.setText(SituationRows.comment(situation));
        mapMeta.setText(SituationRows.meta(situation, false, null, null));
        mapButton.setEnabled(true);
    }
}
