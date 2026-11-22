package com.gcu.trafficscotland.s2531698.logic;

import com.gcu.trafficscotland.s2531698.model.FeedKind;
import com.gcu.trafficscotland.s2531698.model.Situation;

import java.util.ArrayList;
import java.util.List;

/** Road search, feed filter and distance search applied to the parsed list. */
public final class SituationQuery {
    public final FeedKind feed;
    public final String road;
    public final Double latitude;
    public final Double longitude;
    public final Double radiusKm;

    public SituationQuery(FeedKind feed, String road, Double latitude, Double longitude, Double radiusKm) {
        this.feed = feed;
        this.road = road;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
    }

    public boolean usesDistance() {
        return latitude != null && longitude != null && radiusKm != null;
    }

    public List<Situation> apply(List<Situation> input) {
        List<Situation> matches = new ArrayList<>();
        if (input == null) {
            return matches;
        }
        boolean distance = usesDistance();
        for (Situation situation : input) {
            if (situation == null) {
                continue;
            }
            if (feed != null && situation.feed != feed) {
                continue;
            }
            if (!RoadNames.matches(situation, road)) {
                continue;
            }
            if (distance) {
                double kilometres = Geo.distanceToSituationKm(latitude, longitude, situation);
                if (Double.isInfinite(kilometres) || kilometres > radiusKm) {
                    continue;
                }
            }
            matches.add(situation);
        }
        matches.sort((left, right) -> {
            if (distance) {
                double leftKm = Geo.distanceToSituationKm(latitude, longitude, left);
                double rightKm = Geo.distanceToSituationKm(latitude, longitude, right);
                int compared = Double.compare(leftKm, rightKm);
                if (compared != 0) {
                    return compared;
                }
            }
            int byRoad = SituationText.primaryRoad(left).compareToIgnoreCase(SituationText.primaryRoad(right));
            if (byRoad != 0) {
                return byRoad;
            }
            String leftComment = left.comment == null ? "" : left.comment;
            String rightComment = right.comment == null ? "" : right.comment;
            return leftComment.compareToIgnoreCase(rightComment);
        });
        return matches;
    }
}
