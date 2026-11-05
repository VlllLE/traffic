package com.gcu.trafficscotland.ui;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.gcu.trafficscotland.R;
import com.gcu.trafficscotland.logic.SituationText;
import com.gcu.trafficscotland.model.Situation;

import java.util.Locale;

/** Fills the detail screen from one parsed situation. Empty facts are hidden. */
public final class SituationBinder {
    private SituationBinder() {
    }

    public static void bind(Activity activity, Situation situation) {
        ImageView icon = activity.findViewById(R.id.detailIcon);
        TextView title = activity.findViewById(R.id.detailTitle);
        TextView delay = activity.findViewById(R.id.detailDelay);
        TextView comment = activity.findViewById(R.id.detailComment);
        icon.setImageResource(DelayColours.icon(situation));
        title.setText(SituationText.typeLabel(situation));
        comment.setText(SituationRows.comment(situation));

        String delayLabel = SituationText.delayLabel(situation);
        if (delayLabel.isEmpty()) {
            delay.setVisibility(View.GONE);
        } else {
            delay.setVisibility(View.VISIBLE);
            delay.setText(delayLabel);
            delay.setBackgroundColor(DelayColours.stripe(activity, situation));
            delay.setTextColor(DelayColours.onStripe(situation));
        }

        setLine(activity, R.id.detailRoads, "Roads", SituationText.roadsLabel(situation));
        String direction = SituationText.directionLabel(situation.direction);
        String kind = SituationText.pretty(situation.locationKind);
        setLine(activity, R.id.detailDirection, "Location", join(kind, direction));
        setLine(activity, R.id.detailFrom, "From", point(situation.from));
        setLine(activity, R.id.detailTo, "To", point(situation.to));
        setLine(activity, R.id.detailWhen, "When", when(situation));
        setLine(activity, R.id.detailDuration, "Planned length", SituationText.durationLabel(situation));
        setLine(activity, R.id.detailLanes, "Lanes", lanes(situation));
        setLine(activity, R.id.detailCompliance, "Compliance", SituationText.pretty(situation.compliance));
        setLine(activity, R.id.detailProbability, "Likelihood", SituationText.pretty(situation.probability));
        setLine(activity, R.id.detailUrgent, "Urgent roadworks",
                "true".equalsIgnoreCase(situation.urgent) ? "Yes" : "");
        setLine(activity, R.id.detailStatus, "Record", record(situation));
        setLine(activity, R.id.detailSource, "Source", situation.source == null ? "" : situation.source);
        activity.findViewById(R.id.detailMapButton).setVisibility(
                situation.hasAnyCoordinates() ? View.VISIBLE : View.GONE);
    }

    public static void missing(Activity activity) {
        ((TextView) activity.findViewById(R.id.detailTitle)).setText(R.string.unavailable);
        activity.findViewById(R.id.detailDelay).setVisibility(View.GONE);
        ((TextView) activity.findViewById(R.id.detailComment)).setText(R.string.unavailable_body);
        int[] lines = {R.id.detailRoads, R.id.detailDirection, R.id.detailFrom, R.id.detailTo,
                R.id.detailWhen, R.id.detailDuration, R.id.detailLanes, R.id.detailCompliance,
                R.id.detailProbability, R.id.detailUrgent, R.id.detailStatus, R.id.detailSource,
                R.id.detailMapButton};
        for (int id : lines) {
            activity.findViewById(id).setVisibility(View.GONE);
        }
    }

    private static void setLine(Activity activity, int id, String label, String value) {
        TextView view = activity.findViewById(id);
        if (value == null || value.isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(label + ": " + value);
        }
    }

    private static String point(com.gcu.trafficscotland.model.GeoPoint point) {
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
