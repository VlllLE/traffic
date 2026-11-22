package com.gcu.trafficscotland;

import android.app.Application;

import com.gcu.trafficscotland.data.TrafficRepository;

public class TrafficApp extends Application {
    private TrafficRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new TrafficRepository(this);
        repository.start();
    }

    public TrafficRepository repository() {
        return repository;
    }
}
