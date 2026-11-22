package com.gcu.trafficscotland.ui;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.gcu.trafficscotland.R;
import com.gcu.trafficscotland.logic.SituationText;
import com.gcu.trafficscotland.map.OsmMapView;
import com.gcu.trafficscotland.model.Situation;

/** Map view. The situations it draws come from {@link MainViewModel}; taps are sent back as selection. */
public class MapFragment extends Fragment {
    private boolean centered;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainViewModel model = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.map);
        back(toolbar);
        OsmMapView map = view.findViewById(R.id.map);
        TextView title = view.findViewById(R.id.mapTitle);
        TextView comment = view.findViewById(R.id.mapComment);
        TextView meta = view.findViewById(R.id.mapMeta);
        View details = view.findViewById(R.id.mapButton);
        map.setOnSituationClickListener(situation -> model.select(situation.stableId()));
        details.setOnClickListener(v -> model.openDetail());
        view.findViewById(R.id.zoomIn).setOnClickListener(v -> map.zoomBy(1));
        view.findViewById(R.id.zoomOut).setOnClickListener(v -> map.zoomBy(-1));
        view.findViewById(R.id.fitMap).setOnClickListener(v -> map.fitToSituations());
        model.state().observe(getViewLifecycleOwner(), state -> {
            if (!isAdded()) {
                return;
            }
            map.setSituations(state.mapSituations);
            map.setSelectedId(state.selectedId);
            Situation situation = state.selectedSituation;
            if (situation == null) {
                title.setText(R.string.map_hint);
                comment.setText("");
                meta.setText("");
                details.setEnabled(false);
                return;
            }
            title.setText(SituationText.typeLabel(situation));
            comment.setText(SituationRows.comment(situation));
            meta.setText(SituationRows.meta(situation, false, null, null));
            details.setEnabled(true);
            if (!centered) {
                map.centerOn(situation);
                centered = true;
            }
        });
    }

    private void back(Toolbar toolbar) {
        Drawable icon = AppCompatResources.getDrawable(requireContext(),
                androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(Color.WHITE);
            toolbar.setNavigationIcon(icon);
        }
        toolbar.setNavigationContentDescription(androidx.appcompat.R.string.abc_action_bar_up_description);
        toolbar.setNavigationOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
    }
}
