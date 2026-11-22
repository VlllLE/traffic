package com.gcu.trafficscotland.parser;

import com.gcu.trafficscotland.logic.Geo;
import com.gcu.trafficscotland.logic.RoadNames;
import com.gcu.trafficscotland.logic.SituationQuery;
import com.gcu.trafficscotland.logic.SituationText;
import com.gcu.trafficscotland.logic.WebMercator;
import com.gcu.trafficscotland.model.DelayBand;
import com.gcu.trafficscotland.model.FeedKind;
import com.gcu.trafficscotland.model.MarkerKind;
import com.gcu.trafficscotland.model.ParseResult;
import com.gcu.trafficscotland.model.Situation;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DatexPullParserTest {
    private final DatexPullParser parser = new DatexPullParser();

    @Test
    public void unplannedSampleHasFourSituationsWithTypesRoadsAndCoordinates() throws Exception {
        ParseResult result = parser.parse(resource("unplanned.xml"), FeedKind.INCIDENT);
        assertEquals(4, result.situations.size());
        assertEquals("2026-08-31T09:58:26+01:00", result.publicationTime);

        Situation closure = result.situations.get(0);
        assertEquals("I_20260810_248", closure.recordId);
        assertEquals("RoadOrCarriagewayOrLaneManagement", closure.recordType);
        assertEquals("roadClosed", closure.managementType);
        assertEquals("The A74M(S) junction 19 (Ecclefechan) off slip is closed to allow for essential roadworks.",
                closure.comment);
        assertEquals("southBound", closure.direction);
        assertEquals("0", closure.operationalLanes);
        assertEquals("mandatory", closure.compliance);
        assertTrue(closure.to.hasCoordinates);
        assertEquals(55.065432, closure.to.latitude, 0.000001);
        assertEquals(-3.273135, closure.to.longitude, 0.000001);
        assertTrue(RoadNames.sameRoad(SituationText.primaryRoad(closure), "A74(M)"));
        assertEquals(MarkerKind.CLOSURE, SituationText.markerKind(closure));
        assertEquals("Road Closed", SituationText.typeLabel(closure));
        assertEquals("Southbound", SituationText.directionLabel(closure.direction));

        Situation queue = find(result, "I_20260831_129");
        assertEquals("queuingTraffic", queue.abnormalType);
        assertEquals("2", queue.lanesRestricted);
        assertEquals("eastBound", queue.direction);
        assertEquals(MarkerKind.QUEUE, SituationText.markerKind(queue));
        assertTrue(queue.roads.stream().anyMatch(road -> RoadNames.sameRoad(road, "M8")));
        assertEquals("M8 J18 Charing Cross", queue.to.place);
        assertEquals("J19", queue.from.junction);
    }

    @Test
    public void roadworksSampleCountsDelaysAndReadsALongWork() throws Exception {
        ParseResult result = parser.parse(resource("roadworks.xml"), FeedKind.ROADWORKS);
        assertEquals(405, result.situations.size());
        int delays = 0;
        int longDelays = 0;
        int veryLong = 0;
        int none = 0;
        for (Situation situation : result.situations) {
            assertEquals(FeedKind.ROADWORKS, situation.feed);
            assertEquals(MarkerKind.ROADWORKS, SituationText.markerKind(situation));
            switch (SituationText.delayBand(situation)) {
                case SHORT:
                    delays++;
                    break;
                case LONG:
                    longDelays++;
                    break;
                case VERY_LONG:
                    veryLong++;
                    break;
                case NONE:
                    none++;
                    break;
                default:
                    break;
            }
        }
        assertEquals(130, delays);
        assertEquals(26, longDelays);
        assertEquals(1, veryLong);
        assertEquals(248, none);

        Situation fochabers = find(result, "D_254286");
        assertEquals(DelayBand.VERY_LONG, SituationText.delayBand(fochabers));
        assertEquals("Very long delay", SituationText.delayLabel(fochabers));
        assertEquals("false", fochabers.urgent);
        assertEquals("roadworks", fochabers.maintenanceType);
        assertEquals("Eastbound", SituationText.directionLabel(fochabers.direction));
        assertTrue(SituationText.durationLabel(fochabers).endsWith("days"));
        assertEquals("6 Jul 2026, 06:00", SituationText.formatWhen(fochabers.startTime));
        assertTrue(fochabers.hasLine());
        assertTrue(fochabers.roads.stream().anyMatch(road -> RoadNames.sameRoad(road, "A96")));
    }

    @Test
    public void roadSearchDoesNotConfuseA9WithA90AndDistanceFiltersGlasgow() throws Exception {
        List<Situation> all = parser.parse(resource("unplanned.xml"), FeedKind.INCIDENT).situations;
        all.addAll(parser.parse(resource("roadworks.xml"), FeedKind.ROADWORKS).situations);

        SituationQuery a9 = new SituationQuery(null, "A9", null, null, null);
        List<Situation> a9Hits = a9.apply(all);
        assertFalse(a9Hits.isEmpty());
        for (Situation situation : a9Hits) {
            assertTrue(situation.roads.stream().anyMatch(road -> RoadNames.sameRoad(road, "A9")));
            assertFalse(situation.roads.stream().allMatch(road -> RoadNames.sameRoad(road, "A90")));
        }
        assertTrue(a9Hits.stream().noneMatch(situation ->
                situation.comment != null && situation.comment.contains("Brechin") && !situation.roads.stream().anyMatch(r -> RoadNames.sameRoad(r, "A9"))));

        SituationQuery a90 = new SituationQuery(FeedKind.INCIDENT, "a90", null, null, null);
        List<Situation> a90Hits = a90.apply(all);
        assertEquals(1, a90Hits.size());
        assertTrue(a90Hits.get(0).comment.contains("Brechin"));

        SituationQuery a74 = new SituationQuery(null, "A74", null, null, null);
        assertTrue(a74.apply(all).stream().anyMatch(situation -> "I_20260810_248".equals(situation.recordId)));

        SituationQuery near = new SituationQuery(null, "", 55.8609, -4.2518, 5.0);
        List<Situation> nearby = near.apply(all);
        assertTrue(nearby.stream().anyMatch(situation -> "I_20260831_129".equals(situation.recordId)));
        assertTrue(nearby.stream().noneMatch(situation -> situation.comment != null && situation.comment.contains("Brechin")));
        double queueKm = Geo.distanceToSituationKm(55.8609, -4.2518, find(nearby, "I_20260831_129"));
        assertTrue(queueKm < 5.0);

        SituationQuery incidentsOnly = new SituationQuery(FeedKind.INCIDENT, null, null, null, null);
        assertEquals(4, incidentsOnly.apply(all).size());
    }

    @Test
    public void unknownTagsPointLocationsAndGarbagePrefixDoNotCrash() throws Exception {
        String xml = "garbage before the document\n"
                + "<?xml version=\"1.0\"?>"
                + "<d2LogicalModel xmlns=\"http://datex2.eu/schema/2/2_0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<payloadPublication><publicationTime>2026-01-02T03:04:05Z</publicationTime>"
                + "<situation id=\"S1\"><situationRecord xsi:type=\"AbnormalTraffic\" id=\"R1\">"
                + "<generalPublicComment><comment><values><value lang=\"en\">Queue on the A9</value></values></comment></generalPublicComment>"
                + "<groupOfLocations xsi:type=\"Point\"><pointCoordinates>"
                + "<latitude>56.0</latitude><longitude>-4.0</longitude>"
                + "</pointCoordinates></groupOfLocations>"
                + "<mysteryFutureBlock><alpha>42</alpha><value>should-not-be-the-comment</value></mysteryFutureBlock>"
                + "<abnormalTrafficType>queuingTraffic</abnormalTrafficType>"
                + "</situationRecord></situation></payloadPublication></d2LogicalModel>";
        ParseResult result = parser.parse(xml, FeedKind.INCIDENT);
        assertEquals("2026-01-02T03:04:05Z", result.publicationTime);
        assertEquals(1, result.situations.size());
        Situation situation = result.situations.get(0);
        assertEquals("Queue on the A9", situation.comment);
        assertEquals("AbnormalTraffic", situation.recordType);
        assertEquals("Point", situation.locationKind);
        assertTrue(situation.to.hasCoordinates);
        assertEquals(56.0, situation.to.latitude, 0.0001);
        assertEquals(-4.0, situation.to.longitude, 0.0001);
        assertTrue(RoadNames.matches(situation, "A9"));
        assertFalse(RoadNames.matches(situation, "A90"));
        assertEquals("2 Jan 2026, 03:04", SituationText.formatWhen(result.publicationTime));
    }

    @Test
    public void emptyInputAndRoadIdentityHelpers() throws Exception {
        assertTrue(parser.parse(null, FeedKind.INCIDENT).situations.isEmpty());
        assertTrue(parser.parse("   ", FeedKind.ROADWORKS).situations.isEmpty());
        assertTrue(parser.parse("not xml at all", FeedKind.INCIDENT).situations.isEmpty());
        assertTrue(RoadNames.sameRoad("A74", "A74(M)"));
        assertTrue(RoadNames.sameRoad("a74m", "A74(M)"));
        assertFalse(RoadNames.sameRoad("A9", "A90"));
        assertFalse(RoadNames.sameRoad("A9", "A96"));
        assertEquals(128.0, WebMercator.worldX(0, 0), 0.01);
        assertEquals(128.0, WebMercator.worldY(0, 0), 0.01);
        assertEquals(0.0, WebMercator.longitude(WebMercator.worldX(0, 6), 6), 0.0001);
        assertEquals(55.86, WebMercator.latitude(WebMercator.worldY(55.86, 6), 6), 0.0001);
        assertNotNull(SituationText.delayLabel(new Situation()));
    }

    private static Situation find(ParseResult result, String recordId) {
        return find(result.situations, recordId);
    }

    private static Situation find(List<Situation> situations, String recordId) {
        for (Situation situation : situations) {
            if (recordId.equals(situation.recordId)) {
                return situation;
            }
        }
        throw new AssertionError("Missing " + recordId);
    }

    private static String resource(String name) throws Exception {
        InputStream input = DatexPullParserTest.class.getResourceAsStream("/" + name);
        assertNotNull(name, input);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
