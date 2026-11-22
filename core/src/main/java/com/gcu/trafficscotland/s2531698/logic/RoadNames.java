package com.gcu.trafficscotland.s2531698.logic;

import com.gcu.trafficscotland.s2531698.model.Situation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Road names as they appear in DATEX (M8, A90, A74(M), B957).
 * Matching is by whole road identity so a search for A9 does not return A90.
 */
public final class RoadNames {
    private static final Pattern ROAD = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])((?:M|A|B)\\d{1,4}(?:\\s*\\([A-Za-z]\\))?)(?![A-Za-z0-9])");
    private static final Pattern WHOLE_QUERY = Pattern.compile(
            "(?i)^\\s*[MAB]\\d{1,4}(?:\\s*\\([A-Za-z]\\)|[A-Za-z])?\\s*$");

    private RoadNames() {
    }

    public static String canonical(String road) {
        if (road == null) {
            return "";
        }
        String value = road.toUpperCase(Locale.UK).replace(" ", "");
        value = value.replaceAll("\\(([A-Z])\\)", "$1");
        return value;
    }

    /** A74 matches A74(M). A9 does not match A90 or A96. */
    public static boolean sameRoad(String left, String right) {
        String a = canonical(left);
        String b = canonical(right);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return suffixLetter(a, b) || suffixLetter(b, a);
    }

    private static boolean suffixLetter(String shorter, String longer) {
        return longer.startsWith(shorter)
                && longer.length() == shorter.length() + 1
                && Character.isLetter(longer.charAt(longer.length() - 1));
    }

    public static List<String> extract(String text) {
        List<String> found = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        Matcher matcher = ROAD.matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1).toUpperCase(Locale.UK).replace(" ", "");
            boolean already = false;
            for (String existing : found) {
                if (canonical(existing).equals(canonical(token))) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                found.add(token);
            }
        }
        return found;
    }

    public static void addRoad(Situation situation, String raw) {
        if (situation == null || raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        for (String existing : situation.roads) {
            if (canonical(existing).equals(canonical(trimmed))) {
                return;
            }
        }
        situation.roads.add(trimmed);
    }

    /** Pull road identities out of the comment and place names as well as the location codes. */
    public static void harvest(Situation situation) {
        if (situation == null) {
            return;
        }
        harvestText(situation, situation.comment);
        harvestText(situation, situation.from.place);
        harvestText(situation, situation.from.junction);
        harvestText(situation, situation.to.place);
        harvestText(situation, situation.to.junction);
    }

    private static void harvestText(Situation situation, String text) {
        for (String road : extract(text)) {
            addRoad(situation, road);
        }
    }

    public static boolean matches(Situation situation, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        if (situation == null) {
            return false;
        }
        String trimmed = query.trim();
        if (WHOLE_QUERY.matcher(trimmed).matches()) {
            return situationHas(situation, trimmed);
        }
        List<String> codes = extract(trimmed);
        if (!codes.isEmpty()) {
            for (String code : codes) {
                if (situationHas(situation, code)) {
                    return true;
                }
            }
            return false;
        }
        String needle = trimmed.toLowerCase(Locale.UK);
        return contains(situation.comment, needle)
                || contains(situation.from.place, needle)
                || contains(situation.from.junction, needle)
                || contains(situation.to.place, needle)
                || contains(situation.to.junction, needle);
    }

    private static boolean situationHas(Situation situation, String code) {
        for (String road : situation.roads) {
            if (sameRoad(road, code)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.UK).contains(needle);
    }
}
