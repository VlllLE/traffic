package com.gcu.trafficscotland.s2531698.data;

import com.gcu.trafficscotland.s2531698.model.FeedKind;
import com.gcu.trafficscotland.s2531698.model.Situation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable copy of the feeds the repository last published. */
public final class TrafficSnapshot {
    public final List<Situation> situations;
    public final long fetchedAtMillis;
    public final DataOrigin origin;
    public final String notice;
    public final String incidentsPublished;
    public final String roadworksPublished;

    public TrafficSnapshot(List<Situation> situations, long fetchedAtMillis, DataOrigin origin,
                           String notice, String incidentsPublished, String roadworksPublished) {
        this.situations = Collections.unmodifiableList(new ArrayList<>(situations));
        this.fetchedAtMillis = fetchedAtMillis;
        this.origin = origin == null ? DataOrigin.EMPTY : origin;
        this.notice = notice;
        this.incidentsPublished = incidentsPublished;
        this.roadworksPublished = roadworksPublished;
    }

    public static TrafficSnapshot empty() {
        return new TrafficSnapshot(new ArrayList<>(), 0L, DataOrigin.EMPTY, null, null, null);
    }

    public TrafficSnapshot withNotice(String notice) {
        return new TrafficSnapshot(situations, fetchedAtMillis, origin, notice,
                incidentsPublished, roadworksPublished);
    }

    public Situation find(String id) {
        if (id == null) {
            return null;
        }
        for (Situation situation : situations) {
            if (id.equals(situation.stableId())) {
                return situation;
            }
        }
        return null;
    }

    public int count(FeedKind feed) {
        int count = 0;
        for (Situation situation : situations) {
            if (situation.feed == feed) {
                count++;
            }
        }
        return count;
    }
}
