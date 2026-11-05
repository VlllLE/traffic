package com.gcu.trafficscotland.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import com.gcu.trafficscotland.logic.SituationText;
import com.gcu.trafficscotland.model.Situation;

public class DetailActivity extends AppCompatActivity implements TrafficRepository.Listener {
    private static final String EXTRA_ID = "situationId";

    private TrafficRepository repository;
    private String situationId = "";

    public static Intent intent(Context context, String situationId) {
        return new Intent(context, DetailActivity.class).putExtra(EXTRA_ID, situationId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        repository = ((TrafficApp) getApplication()).repository();
        situationId = getIntent().getStringExtra(EXTRA_ID);
        if (situationId == null) {
            situationId = "";
        }
        findViewById(R.id.detailMapButton).setOnClickListener(v ->
                startActivity(MapActivity.intent(this, false, null, "", false,
                        MainActivity.GLASGOW_LAT, MainActivity.GLASGOW_LON, 25, situationId)));
        show(repository.snapshot());
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
        show(snapshot);
    }

    private void show(TrafficSnapshot snapshot) {
        Situation situation = snapshot.find(situationId);
        if (situation == null) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.unavailable);
                getSupportActionBar().setSubtitle(null);
            }
            SituationBinder.missing(this);
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(SituationText.typeLabel(situation));
            String roads = SituationText.roadsLabel(situation);
            getSupportActionBar().setSubtitle(roads.isEmpty() ? null : roads);
        }
        SituationBinder.bind(this, situation);
        findViewById(R.id.detailMapButton).setOnClickListener(v ->
                startActivity(MapActivity.intent(this, false, null, "", false,
                        MainActivity.GLASGOW_LAT, MainActivity.GLASGOW_LON, 25, situationId)));
    }
}
