package com.gcu.trafficscotland.s2531698.model;

/**
 * Colour band for roadworks delays. Mapped from the DATEX delaysType value:
 * delays, longDelays, veryLongDelays. Unknown future values stay UNKNOWN
 * so the app can show them without crashing.
 */
public enum DelayBand {
    NONE,
    SHORT,
    LONG,
    VERY_LONG,
    UNKNOWN
}
