package com.gcu.trafficscotland.s2531698.model;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One DATEX situation record after PullParser extraction.
 * Fields that are not present in a particular record stay null.
 * The class ignores nothing by crashing: unknown XML is simply never stored.
 */
public class Situation {
    public String situationId;
    public String recordId;
    public FeedKind feed;
    public String recordType;
    public String comment;
    public String direction;
    public String delayType;
    public String startTime;
    public String endTime;
    public String probability;
    public String compliance;
    public String managementType;
    public String abnormalType;
    public String maintenanceType;
    public String urgent;
    public String operationalLanes;
    public String lanesRestricted;
    public String confidentiality;
    public String informationStatus;
    public String validityStatus;
    public String source;
    public String createdTime;
    public String versionTime;
    public String locationKind;
    public final GeoPoint from = new GeoPoint();
    public final GeoPoint to = new GeoPoint();
    public final Set<String> roads = new LinkedHashSet<>();

    public String stableId() {
        if (recordId != null && !recordId.isEmpty()) {
            return recordId;
        }
        if (situationId != null && !situationId.isEmpty()) {
            return situationId;
        }
        return "";
    }

    public boolean hasLine() {
        return from.hasCoordinates && to.hasCoordinates;
    }

    public boolean hasAnyCoordinates() {
        return from.hasCoordinates || to.hasCoordinates;
    }
}
