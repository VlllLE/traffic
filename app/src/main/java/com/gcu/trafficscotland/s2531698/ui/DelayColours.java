package com.gcu.trafficscotland.s2531698.ui;

import android.content.Context;
import android.graphics.Color;

import com.gcu.trafficscotland.s2531698.R;
import com.gcu.trafficscotland.s2531698.logic.SituationText;
import com.gcu.trafficscotland.s2531698.model.DelayBand;
import com.gcu.trafficscotland.s2531698.model.MarkerKind;
import com.gcu.trafficscotland.s2531698.model.Situation;

/** Colours for the roadworks delay bands and the incident icons. */
public final class DelayColours {
    private DelayColours() {
    }

    public static int stripe(Context context, Situation situation) {
        if (SituationText.markerKind(situation) == MarkerKind.ROADWORKS) {
            switch (SituationText.delayBand(situation)) {
                case SHORT:
                    return context.getColor(R.color.delay_short);
                case LONG:
                    return context.getColor(R.color.delay_long);
                case VERY_LONG:
                    return context.getColor(R.color.delay_very_long);
                case UNKNOWN:
                    return context.getColor(R.color.delay_unknown);
                case NONE:
                default:
                    return context.getColor(R.color.delay_none);
            }
        }
        switch (SituationText.markerKind(situation)) {
            case CLOSURE:
                return context.getColor(R.color.closure);
            case QUEUE:
                return context.getColor(R.color.queue);
            case LANES:
                return context.getColor(R.color.lanes);
            case ROADWORKS:
                return context.getColor(R.color.delay_long);
            case OTHER:
            default:
                return context.getColor(R.color.other);
        }
    }

    public static int onStripe(Situation situation) {
        DelayBand band = SituationText.delayBand(situation);
        if (SituationText.markerKind(situation) == MarkerKind.ROADWORKS
                && (band == DelayBand.NONE || band == DelayBand.SHORT)) {
            return Color.BLACK;
        }
        if (SituationText.markerKind(situation) == MarkerKind.QUEUE) {
            return Color.BLACK;
        }
        return Color.WHITE;
    }

    public static int icon(Situation situation) {
        switch (SituationText.markerKind(situation)) {
            case ROADWORKS:
                return R.drawable.ic_roadworks;
            case CLOSURE:
                return R.drawable.ic_closure;
            case QUEUE:
                return R.drawable.ic_queue;
            case LANES:
                return R.drawable.ic_lanes;
            case OTHER:
            default:
                return R.drawable.ic_incident;
        }
    }
}
