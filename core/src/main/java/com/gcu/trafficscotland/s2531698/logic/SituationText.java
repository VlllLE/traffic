package com.gcu.trafficscotland.logic;

import com.gcu.trafficscotland.model.DelayBand;
import com.gcu.trafficscotland.model.FeedKind;
import com.gcu.trafficscotland.model.MarkerKind;
import com.gcu.trafficscotland.model.Situation;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Display strings derived from a parsed situation. Kept off the activities so it can be tested. */
public final class SituationText {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK);

    private SituationText() {
    }

    public static String typeLabel(Situation situation) {
        if (situation == null) {
            return "Situation";
        }
        if (situation.abnormalType != null) {
            return pretty(situation.abnormalType);
        }
        if (situation.managementType != null) {
            return pretty(situation.managementType);
        }
        if (situation.maintenanceType != null) {
            return pretty(situation.maintenanceType);
        }
        if (situation.recordType != null) {
            return pretty(situation.recordType);
        }
        return "Situation";
    }

    public static String directionLabel(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.endsWith("Bound") && raw.length() > 5) {
            String head = raw.substring(0, raw.length() - 5);
            return capitalize(head) + "bound";
        }
        return pretty(raw);
    }

    public static DelayBand delayBand(Situation situation) {
        if (situation == null || situation.delayType == null || situation.delayType.isEmpty()) {
            return DelayBand.NONE;
        }
        switch (situation.delayType) {
            case "delays":
                return DelayBand.SHORT;
            case "longDelays":
                return DelayBand.LONG;
            case "veryLongDelays":
                return DelayBand.VERY_LONG;
            default:
                return DelayBand.UNKNOWN;
        }
    }

    public static String delayLabel(Situation situation) {
        switch (delayBand(situation)) {
            case SHORT:
                return "Short delay";
            case LONG:
                return "Long delay";
            case VERY_LONG:
                return "Very long delay";
            case UNKNOWN:
                return pretty(situation.delayType);
            case NONE:
            default:
                if (situation != null && situation.feed == FeedKind.ROADWORKS) {
                    return "No delay reported";
                }
                return "";
        }
    }

    public static MarkerKind markerKind(Situation situation) {
        if (situation == null) {
            return MarkerKind.OTHER;
        }
        if (situation.feed == FeedKind.ROADWORKS
                || situation.maintenanceType != null
                || "MaintenanceWorks".equals(situation.recordType)) {
            return MarkerKind.ROADWORKS;
        }
        String management = situation.managementType == null
                ? "" : situation.managementType.toLowerCase(Locale.UK);
        String abnormal = situation.abnormalType == null
                ? "" : situation.abnormalType.toLowerCase(Locale.UK);
        if (management.contains("closed") || "0".equals(situation.operationalLanes)) {
            return MarkerKind.CLOSURE;
        }
        if (abnormal.contains("queu") || abnormal.contains("stationary") || abnormal.contains("slow")) {
            return MarkerKind.QUEUE;
        }
        if (situation.lanesRestricted != null || management.contains("lane")) {
            return MarkerKind.LANES;
        }
        return MarkerKind.OTHER;
    }

    public static String roadsLabel(Situation situation) {
        if (situation == null || situation.roads.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String road : situation.roads) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(road);
        }
        return builder.toString();
    }

    public static String primaryRoad(Situation situation) {
        if (situation == null || situation.roads.isEmpty()) {
            return "";
        }
        return situation.roads.iterator().next();
    }

    public static String formatWhen(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        OffsetDateTime parsed = parseTime(raw);
        if (parsed == null) {
            return raw;
        }
        return WHEN.format(parsed);
    }

    public static String durationLabel(Situation situation) {
        if (situation == null) {
            return "";
        }
        OffsetDateTime start = parseTime(situation.startTime);
        OffsetDateTime end = parseTime(situation.endTime);
        if (start == null || end == null) {
            return "";
        }
        Duration duration = Duration.between(start, end);
        if (duration.isNegative() || duration.isZero()) {
            return "";
        }
        long days = duration.toDays();
        if (days >= 1) {
            return days == 1 ? "1 day" : days + " days";
        }
        long hours = duration.toHours();
        if (hours >= 1) {
            return hours == 1 ? "1 hour" : hours + " hours";
        }
        long minutes = Math.max(1, duration.toMinutes());
        return minutes == 1 ? "1 min" : minutes + " min";
    }

    public static String pretty(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String spaced = raw.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        return capitalize(spaced);
    }

    public static OffsetDateTime parseTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.UK) + value.substring(1);
    }
}
