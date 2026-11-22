package com.gcu.trafficscotland.s2531698.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gcu.trafficscotland.s2531698.R;
import com.gcu.trafficscotland.s2531698.logic.SituationText;
import com.gcu.trafficscotland.s2531698.model.Situation;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class SituationAdapter extends RecyclerView.Adapter<SituationAdapter.Holder> {
    public interface OnSituationClick {
        void onSituationClick(Situation situation);
    }

    private final List<Situation> items = new ArrayList<>();
    private final OnSituationClick click;
    private String selectedId = "";
    private boolean showDistance;
    private double latitude;
    private double longitude;

    public SituationAdapter(OnSituationClick click) {
        this.click = click;
    }

    public void submit(List<Situation> next, String selectedId, boolean showDistance, double latitude, double longitude) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        this.selectedId = selectedId == null ? "" : selectedId;
        this.showDistance = showDistance;
        this.latitude = latitude;
        this.longitude = longitude;
        notifyDataSetChanged();
    }

    public int positionOf(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).stableId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_situation, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Situation situation = items.get(position);
        boolean selected = situation.stableId().equals(selectedId);
        holder.type.setText(SituationText.typeLabel(situation));
        holder.comment.setText(SituationRows.comment(situation));
        holder.meta.setText(SituationRows.meta(situation, showDistance, latitude, longitude));
        holder.stripe.setBackgroundColor(DelayColours.stripe(holder.itemView.getContext(), situation));
        holder.icon.setImageResource(DelayColours.icon(situation));
        int stroke = selected ? holder.itemView.getResources().getDimensionPixelSize(R.dimen.selected_stroke) : 0;
        holder.card.setStrokeWidth(stroke);
        holder.card.setStrokeColor(holder.itemView.getContext().getColor(R.color.brand));
        holder.itemView.setOnClickListener(v -> click.onSituationClick(situation));
        holder.itemView.setContentDescription(SituationText.typeLabel(situation) + ". " + SituationRows.comment(situation));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final View stripe;
        final ImageView icon;
        final TextView type;
        final TextView comment;
        final TextView meta;

        Holder(View view) {
            super(view);
            card = (MaterialCardView) view;
            stripe = view.findViewById(R.id.itemStripe);
            icon = view.findViewById(R.id.itemIcon);
            type = view.findViewById(R.id.itemType);
            comment = view.findViewById(R.id.itemComment);
            meta = view.findViewById(R.id.itemMeta);
        }
    }
}
