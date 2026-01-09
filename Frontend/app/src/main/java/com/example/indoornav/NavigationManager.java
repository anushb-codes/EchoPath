package com.example.indoornav;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.TextView;

import java.util.ArrayList;

public class NavigationManager implements SensorEventListener {

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final SpeechManager speechManager;
    private final TextView txtInstruction;

    private final ArrayList<DirectionSegment> segments = new ArrayList<>();

    private int pathIndex = 0;
    private int stepCount = 0;
    private int currentSegmentTargetSteps = 0;
    private boolean isNavigating = false;
    private double magnitudePrevious = 0;

    private String destination;

    public String getCurrentNode() {
        return currentNode;
    }

    private String currentNode = "Entrance";

    public String getLastInsruction() {
        return lastInsruction;
    }

    private String lastInsruction = "No instruction to repeat.";

    public NavigationManager(Context ctx, TextView txt, SpeechManager sm) {
        txtInstruction = txt;
        speechManager = sm;

        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void loadRoute(ArrayList<DirectionSegment> route, String dest) {
        segments.clear();
        segments.addAll(route);
        destination = dest;
    }

    public void start() {
        if (segments.isEmpty()) return;

        isNavigating = true;
        pathIndex = 0;
        stepCount = 0;

        loadSegment();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void loadSegment() {
        DirectionSegment seg = segments.get(pathIndex);
        currentSegmentTargetSteps = seg.steps;

        String msg = "Walk " + humanize(seg.direction)
                + " for " + seg.steps + " steps towards " + seg.to;
        lastInsruction = msg;
        txtInstruction.setText(msg);
        speechManager.speak(msg);
    }

    private String humanize(String dir) {
        switch (dir) {
            case "left": return "to your left";
            case "right": return "to your right";
            case "straight": return "straight ahead";
            case "back": return "backwards";
            case "upstairs": return "up the stairs";
            case "downstairs": return "down the stairs";
            default: return "forward";
        }
    }

    // ✅ STEP LOGIC — UNCHANGED
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isNavigating) return;

        double mag = Math.sqrt(
                event.values[0]*event.values[0] +
                        event.values[1]*event.values[1] +
                        event.values[2]*event.values[2]);

        double delta = mag - magnitudePrevious;
        magnitudePrevious = mag;

        if (delta > 2) {
            stepCount++;
            txtInstruction.setText("Steps: " + stepCount + " / " + currentSegmentTargetSteps);

            if (stepCount >= currentSegmentTargetSteps) {
                advance();
            }
        }
    }

    private void advance() {
        stepCount = 0;
        pathIndex++;

        if (pathIndex >= segments.size()) {
            isNavigating = false;
            sensorManager.unregisterListener(this);
            currentNode = destination;
            String msg = "You have arrived at " + destination;
            txtInstruction.setText(msg);
            speechManager.speak(msg);
            return;
        }

        currentNode = segments.get(pathIndex).from;
        loadSegment();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void stop() {
        isNavigating = false;
        sensorManager.unregisterListener(this);
    }

    // ===== Model =====
    public static class DirectionSegment {
        public String from, to, direction;
        public int steps;

        public DirectionSegment(String f, String t, String d, int s) {
            from = f;
            to = t;
            direction = d;
            steps = s;
        }
    }
}
