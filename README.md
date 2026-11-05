# Traffic Scotland

Android app for the Mobile Platform Development coursework. It reads the Traffic Scotland DATEX II unplanned-events and current-roadworks feeds with `XmlPullParser`, keeps the parsed situations in model classes, and shows them in a list, a detail screen and a map.

## Open in Android Studio

1. Android Studio: File → Open, and choose this folder (`TrafficScotland`).
2. Let Gradle sync. The project targets Android 8.0 (API 26) and above.
3. Copy `datex.properties.example` to `datex.properties` in this folder.
4. Put your DATEX client id and key in that file. It is gitignored, so the keys are not committed.
5. Build the project again. The id and key are compiled into the app from that file.
6. Run it on an emulator or a phone.

Until `datex.properties` is filled in, the app shows the coursework sample XML (or the last feed it managed to save) and says so on screen. With valid keys it downloads:

- `https://datex2.trafficscotland.org/rest/2.3/publications/UnplannedEvents/Content.xml`
- `https://datex2.trafficscotland.org/rest/2.3/publications/Roadworks/Content.xml`

If the roadworks publication on your portal uses a different name, change `ROADWORKS_URL` in `DatexDownloader`.

## What the app does

- Lists every situation with its type and public comment, and an icon for the kind of situation.
- Opens a detail screen with the roads, direction, from/to coordinates, times, lanes, delay and source fields that were present.
- Colours roadworks by delay: green when none is reported, amber for short (`delays`), orange for long (`longDelays`), red for very long (`veryLongDelays`).
- Searches by road name. `A9` does not match `A90`. `A74` matches `A74(M)`.
- Optional distance search around a fixed Glasgow point (55.861, -4.252) or the phone’s coarse location.
- Draws situations on a pan-and-zoom map, including the line between the from and to points.
- Portrait and landscape use different layouts. Landscape keeps the list beside the map.
- Refreshes on a 5 minute timer, on a background download thread and a separate parse thread. The last successful download is saved and reused when the network fails.

## Checks

Parser, road search, distance and map maths are in the `core` module:

```
./gradlew :core:test
```

## Before you hand it in

The brief asks for the project folder and package to use `Surname_GivenName_StudentId`. Rename those, and fill in the name block at the top of `MainActivity`, before submission. The brief also asks you to say how generative AI was used. This project was produced with Cursor’s coding agent (Grok), which wrote the app from the coursework brief, the skeleton and the two sample XML files. Keep the prompts you used and describe that in the submission document.
