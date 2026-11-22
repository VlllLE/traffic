package com.gcu.trafficscotland.ui;

import com.gcu.trafficscotland.logic.Geo;
import com.gcu.trafficscotland.logic.SituationText;
import com.gcu.trafficscotland.model.DelayBand;
import com.gcu.trafficscotland.model.Situation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Short lines shared by the list, the landscape preview and the map card. */
public final class SituationRows {
    private SituationRows() {
    }

    public static String comment(Situation situation) {
        if (situation == null || situation.comment == null || situation.comment.trim().isEmpty()) {
            return "No description";
        }
        return situation.comment.replace('\n', ' ').trim();
    }

    public static String meta(Situation situation, boolean showDistance, Double latitude, Double longitude) {
        List<String> parts = new ArrayList<>();
        if (showDistance && latitude != null && longitude != null) {
            double kilometres = Geo.distanceToSituationKm(latitude, longitude, situation);
            if (!Double.isInfinite(kilometres)) {
                parts.add(String.format(Locale.UK, "%.1f km", kilometres));
            }
        }
        String roads = SituationText.roadsLabel(situation);
        if (!roads.isEmpty()) {
            parts.add(roads);
        }
        String direction = SituationText.directionLabel(situation.direction);
        if (!direction.isEmpty()) {
            parts.add(direction);
        }
        if (SituationText.delayBand(situation) != DelayBand.NONE) {
            String delay = SituationText.delayLabel(situation);
            if (!delay.isEmpty()) {
                parts.add(delay);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(part);
        }
        return builder.toString();
    }
}
