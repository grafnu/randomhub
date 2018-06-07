package com.example.androidthings.sensorhub.collector;

import com.example.androidthings.sensorhub.SensorData;

import java.util.Collections;
import java.util.List;

/**
 */

public class RandomNumberCollector implements SensorCollector {
    private static final String SENSOR_NAME = "random";
    private boolean enabledState = true;

    @Override
    public boolean activate() {
        return true;
    }

    @Override
    public void setEnabled(String sensor, boolean enabled) {
        enabledState = enabled;
    }

    @Override
    public boolean isEnabled(String sensor) {
        return enabledState;
    }

    @Override
    public List<String> getAvailableSensors() {
        return Collections.singletonList(SENSOR_NAME);
    }

    @Override
    public List<String> getEnabledSensors() {
        if (enabledState) {
            return getAvailableSensors();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void collectRecentReadings(List<SensorData> output) {
        float sensorValue = (float) Math.random();
        output.add(new SensorData(SENSOR_NAME, sensorValue));
    }

    @Override
    public void closeQuietly() {

    }
}
