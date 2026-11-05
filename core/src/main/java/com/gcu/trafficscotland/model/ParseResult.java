package com.gcu.trafficscotland.model;

import java.util.ArrayList;
import java.util.List;

/** Situations read from one DATEX publication string, plus its publication time. */
public class ParseResult {
    public final List<Situation> situations = new ArrayList<>();
    public String publicationTime;
}
