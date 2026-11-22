package com.gcu.trafficscotland.s2531698.ui;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.gcu.trafficscotland.s2531698.R;
import com.gcu.trafficscotland.s2531698.logic.SituationText;
import com.gcu.trafficscotland.s2531698.model.Situation;

/** Detail view. It renders one situation from {@link MainViewModel} and sends the map action back. */
public class DetailFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainViewModel model = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        back(toolbar);
        view.findViewById(R.id.detailMapButton).setOnClickListener(v ->
                model.openMap(MainViewModel.MapFilter.ALL));
        model.state().observe(getViewLifecycleOwner(), state -> {
            if (!isAdded()) {
                return;
            }
            Situation situation = state.selectedSituation;
            if (situation == null) {
                toolbar.setTitle(R.string.unavailable);
                toolbar.setSubtitle(null);
                SituationBinder.missing(view);
                return;
            }
            toolbar.setTitle(SituationText.typeLabel(situation));
            String roads = SituationText.roadsLabel(situation);
            toolbar.setSubtitle(roads.isEmpty() ? null : roads);
            SituationBinder.bind(view, situation);
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
