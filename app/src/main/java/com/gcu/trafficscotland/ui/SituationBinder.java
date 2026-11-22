package com.gcu.trafficscotland.ui;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.gcu.trafficscotland.R;
import com.gcu.trafficscotland.logic.SituationText;
import com.gcu.trafficscotland.model.GeoPoint;
import com.gcu.trafficscotland.model.Situation;

import java.util.Locale;

/** Fills the detail view from one parsed situation. Empty facts are hidden. */
public final class SituationBinder {
    private SituationBinder() {
    }

    public static void bind(View root, Situation situation) {
        ImageView icon = root.findViewById(R.id.detailIcon);
        TextView title = root.findViewById(R.id.detailTitle);
        TextView delay = root.findViewById(R.id.detailDelay);
        TextView comment = root.findViewById(R.id.detailComment);
        icon.setImageResource(DelayColours.icon(situation));
        title.setText(SituationText.typeLabel(situation));
        comment.setText(SituationRows.comment(situation));

        String delayLabel = SituationText.delayLabel(situation);
        if (delayLabel.isEmpty()) {
            delay.setVisibility(View.GONE);
        } else {
            delay.setVisibility(View.VISIBLE);
            delay.setText(delayLabel);
            delay.setBackgroundColor(DelayColours.stripe(root.getContext(), situation));
            delay.setTextColor(DelayColours.onStripe(situation));
        }

        setLine(root, R.id.detailRoads, "Roads", SituationText.roadsLabel(situation));
        String direction = SituationText.directionLabel(situation.direction);
        String kind = SituationText.pretty(situation.locationKind);
        setLine(root, R.id.detailDirection, "Location", join(kind, direction));
        setLine(root, R.id.detailFrom, "From", point(situation.from));
        setLine(root, R.id.detailTo, "To", point(situation.to));
        setLine(root, R.id.detailWhen, "When", when(situation));
        setLine(root, R.id.detailDuration, "Planned length", SituationText.durationLabel(situation));
        setLine(root, R.id.detailLanes, "Lanes", lanes(situation));
        setLine(root, R.id.detailCompliance, "Compliance", SituationText.pretty(situation.compliance));
        setLine(root, R.id.detailProbability, "Likelihood", SituationText.pretty(situation.probability));
        setLine(root, R.id.detailUrgent, "Urgent roadworks",
                "true".equalsIgnoreCase(situation.urgent) ? "Yes" : "");
        setLine(root, R.id.detailStatus, "Record", record(situation));
        setLine(root, R.id.detailSource, "Source", situation.source == null ? "" : situation.source);
        root.findViewById(R.id.detailMapButton).setVisibility(
                situation.hasAnyCoordinates() ? View.VISIBLE : View.GONE);
    }

    public static void missing(View root) {
        ((TextView) root.findViewById(R.id.detailTitle)).setText(R.string.unavailable);
        root.findViewById(R.id.detailDelay).setVisibility(View.GONE);
        ((TextView) root.findViewById(R.id.detailComment)).setText(R.string.unavailable_body);
        int[] lines = {R.id.detailRoads, R.id.detailDirection, R.id.detailFrom, R.id.detailTo,
                R.id.detailWhen, R.id.detailDuration, R.id.detailLanes, R.id.detailCompliance,
                R.id.detailProbability, R.id.detailUrgent, R.id.detailStatus, R.id.detailSource,
                R.id.detailMapButton};
        for (int id : lines) {
            root.findViewById(id).setVisibility(View.GONE);
        }
    }

    private static void setLine(View root, int id, String label, String value) {
        TextView view = root.findViewById(id);
        if (value == null || value.isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(label + ": " + value);
        }
    }

    private static String point(GeoPoint point) {
        if (point == null) {
            return "";
        }
        String name = point.label();
        if (!point.hasCoordinates) {
            return name;
        }
        String coordinates = String.format(Locale.UK, "%.5f, %.5f", point.latitude, point.longitude);
        if (name.isEmpty() || name.equals(coordinates)) {
            return coordinates;
        }
        return name + " (" + coordinates + ")";
    }

    private static String when(Situation situation) {
        String start = SituationText.formatWhen(situation.startTime);
        String end = SituationText.formatWhen(situation.endTime);
        if (start.isEmpty() && end.isEmpty()) {
            return "";
        }
        if (end.isEmpty()) {
            return "starts " + start;
        }
        if (start.isEmpty()) {
            return "ends " + end;
        }
        return start + " to " + end;
    }

    private static String lanes(Situation situation) {
        StringBuilder builder = new StringBuilder();
        if (situation.operationalLanes != null && !situation.operationalLanes.isEmpty()) {
            builder.append("operational ").append(situation.operationalLanes);
        }
        if (situation.lanesRestricted != null && !situation.lanesRestricted.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("restricted ").append(situation.lanesRestricted);
        }
        return builder.toString();
    }

    private static String record(Situation situation) {
        StringBuilder builder = new StringBuilder();
        if (situation.stableId() != null && !situation.stableId().isEmpty()) {
            builder.append(situation.stableId());
        }
        String updated = SituationText.formatWhen(situation.versionTime);
        if (!updated.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append("updated ").append(updated);
        }
        String status = SituationText.pretty(situation.informationStatus);
        if (!status.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(status);
        }
        return builder.toString();
    }

    private static String join(String left, String right) {
        if (left == null || left.isEmpty()) {
            return right == null ? "" : right;
        }
        if (right == null || right.isEmpty()) {
            return left;
        }
        return left + ", " + right;
    }
}
