package com.gcu.trafficscotland.parser;

import com.gcu.trafficscotland.logic.RoadNames;
import com.gcu.trafficscotland.model.FeedKind;
import com.gcu.trafficscotland.model.GeoPoint;
import com.gcu.trafficscotland.model.ParseResult;
import com.gcu.trafficscotland.model.Situation;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;

/**
 * Reads a DATEX II publication that is already held in a String.
 * Parsing uses XmlPullParser only. Tags this version does not know about are skipped,
 * which keeps a feed change from crashing the app.
 */
public class DatexPullParser {

    public ParseResult parse(String xml, FeedKind feed) throws XmlPullParserException, IOException {
        ParseResult result = new ParseResult();
        if (xml == null) {
            return result;
        }
        String document = xml;
        int declaration = document.indexOf("<?");
        if (declaration > 0) {
            document = document.substring(declaration);
        } else if (declaration < 0 && document.indexOf('<') < 0) {
            return result;
        }

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(document));

        ArrayDeque<String> stack = new ArrayDeque<>();
        StringBuilder text = new StringBuilder();
        Walk walk = new Walk();
        walk.feed = feed;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                text.setLength(0);
                String name = parser.getName() == null ? "" : parser.getName();
                stack.push(name);
                onStart(parser, name, walk);
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                if (parser.getText() != null) {
                    text.append(parser.getText());
                }
            } else if (event == XmlPullParser.END_TAG) {
                String name = parser.getName() == null ? "" : parser.getName();
                String value = text.toString().trim();
                text.setLength(0);
                onEnd(name, value, stack, walk, result);
                popUntil(stack, name);
            }
            event = parser.next();
        }
        return result;
    }

    private static void onStart(XmlPullParser parser, String name, Walk walk) {
        if ("situation".equals(name)) {
            walk.situationId = attribute(parser, "id");
            walk.current = null;
            walk.side = null;
        } else if ("situationRecord".equals(name)) {
            Situation situation = new Situation();
            situation.feed = walk.feed;
            situation.situationId = walk.situationId;
            situation.recordType = attribute(parser, "type");
            situation.recordId = attribute(parser, "id");
            walk.current = situation;
            walk.side = null;
            walk.pendingValue = null;
            walk.pendingParent = null;
        } else if ("groupOfLocations".equals(name) && walk.current != null) {
            walk.current.locationKind = attribute(parser, "type");
        } else if ("to".equals(name) || "from".equals(name)) {
            walk.side = name;
        } else if ("ilc".equals(name) || "name".equals(name) || "otherName".equals(name)) {
            walk.pendingParent = name;
            walk.pendingValue = null;
        }
    }

    private static void onEnd(String name, String value, ArrayDeque<String> stack, Walk walk, ParseResult result) {
        if ("publicationTime".equals(name) && !value.isEmpty()) {
            result.publicationTime = value;
        }
        Situation current = walk.current;
        if (current != null && !value.isEmpty()) {
            switch (name) {
                case "latitude":
                    setLatitude(current, walk.side, value);
                    break;
                case "longitude":
                    setLongitude(current, walk.side, value);
                    break;
                case "tpegDirection":
                    current.direction = value;
                    break;
                case "delaysType":
                    current.delayType = value;
                    break;
                case "overallStartTime":
                    current.startTime = value;
                    break;
                case "overallEndTime":
                    current.endTime = value;
                    break;
                case "probabilityOfOccurrence":
                    current.probability = value;
                    break;
                case "numberOfOperationalLanes":
                    current.operationalLanes = value;
                    break;
                case "numberOfLanesRestricted":
                    current.lanesRestricted = value;
                    break;
                case "complianceOption":
                    current.compliance = value;
                    break;
                case "roadOrCarriagewayOrLaneManagementType":
                    current.managementType = value;
                    break;
                case "abnormalTrafficType":
                    current.abnormalType = value;
                    break;
                case "roadMaintenanceType":
                    current.maintenanceType = value;
                    break;
                case "urgentRoadworks":
                    current.urgent = value;
                    break;
                case "confidentiality":
                    current.confidentiality = value;
                    break;
                case "informationStatus":
                    current.informationStatus = value;
                    break;
                case "validityStatus":
                    current.validityStatus = value;
                    break;
                case "sourceIdentification":
                    current.source = value;
                    break;
                case "situationRecordCreationTime":
                    current.createdTime = value;
                    break;
                case "situationRecordVersionTime":
                    current.versionTime = value;
                    break;
                case "value":
                    if (contains(stack, "generalPublicComment")) {
                        if (current.comment == null || current.comment.isEmpty()) {
                            current.comment = value;
                        } else {
                            current.comment = current.comment + "\n" + value;
                        }
                    } else if (contains(stack, "groupOfLocations")) {
                        walk.pendingValue = value;
                        walk.pendingParent = nearestDescriptorParent(stack);
                    }
                    break;
                case "tpegIlcPointDescriptorType":
                case "tpegOtherPointDescriptorType":
                case "tpegJunctionPointDescriptorType":
                    classify(current, walk.side, walk.pendingParent, walk.pendingValue, value);
                    walk.pendingValue = null;
                    break;
                default:
                    break;
            }
        }
        if ("situationRecord".equals(name) && walk.current != null) {
            RoadNames.harvest(walk.current);
            result.situations.add(walk.current);
            walk.current = null;
            walk.side = null;
        } else if ("situation".equals(name)) {
            walk.situationId = null;
        } else if ("to".equals(name) || "from".equals(name)) {
            if (name.equals(walk.side)) {
                walk.side = null;
            }
        }
    }

    private static void classify(Situation situation, String side, String parent, String pending, String type) {
        if (pending == null || pending.isEmpty()) {
            return;
        }
        GeoPoint point = pointFor(situation, side);
        String kind = type == null ? "" : type;
        switch (kind) {
            case "tpegIlcName1":
            case "tpegIlcName2":
            case "linkName":
                RoadNames.addRoad(situation, pending);
                break;
            case "junctionName":
                point.junction = pending;
                break;
            case "intersectionName":
            case "nonLinkedPointName":
                if (point.place == null || point.place.isEmpty()) {
                    point.place = pending;
                } else if (!point.place.contains(pending)) {
                    point.place = point.place + " · " + pending;
                }
                break;
            default:
                if ("ilc".equals(parent)) {
                    RoadNames.addRoad(situation, pending);
                } else if (point.place == null || point.place.isEmpty()) {
                    point.place = pending;
                }
                break;
        }
    }

    private static GeoPoint pointFor(Situation situation, String side) {
        return "from".equals(side) ? situation.from : situation.to;
    }

    private static void setLatitude(Situation situation, String side, String value) {
        try {
            GeoPoint point = pointFor(situation, side);
            point.latitude = Double.parseDouble(value);
            point.sawLatitude = true;
            point.hasCoordinates = point.sawLatitude && point.sawLongitude;
        } catch (NumberFormatException ignored) {
            // A non-numeric coordinate is skipped rather than failing the whole feed.
        }
    }

    private static void setLongitude(Situation situation, String side, String value) {
        try {
            GeoPoint point = pointFor(situation, side);
            point.longitude = Double.parseDouble(value);
            point.sawLongitude = true;
            point.hasCoordinates = point.sawLatitude && point.sawLongitude;
        } catch (NumberFormatException ignored) {
            // See setLatitude.
        }
    }

    private static String nearestDescriptorParent(ArrayDeque<String> stack) {
        for (String name : stack) {
            if ("ilc".equals(name) || "name".equals(name) || "otherName".equals(name)) {
                return name;
            }
        }
        return null;
    }

    private static boolean contains(ArrayDeque<String> stack, String name) {
        return stack.contains(name);
    }

    private static void popUntil(ArrayDeque<String> stack, String name) {
        while (!stack.isEmpty() && !name.equals(stack.peek())) {
            stack.pop();
        }
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * xsi:type and id are attributes on the start tag. The local name is matched
     * so a change in attribute order does not pick up the wrong value.
     * XmlPullParser.getAttributeValue(index) is the call that reads each one.
     */
    static String attribute(XmlPullParser parser, String localName) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String name = parser.getAttributeName(i);
            if (localName.equals(name) || (name != null && name.endsWith(":" + localName))) {
                return parser.getAttributeValue(i);
            }
        }
        return null;
    }

    private static final class Walk {
        private FeedKind feed;
        private Situation current;
        private String situationId;
        private String side;
        private String pendingValue;
        private String pendingParent;
    }
}
