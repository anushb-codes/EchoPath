package com.example.indoornav;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

public class NavigationManager implements SensorEventListener {

    // ===== OpenAI CONFIG =====
    private static final String OPENAI_API_KEY = "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";
    private static final String OPENAI_URL = "https://models.github.ai/inference/v1/chat/completions";

    // ===== SENSORS & UI =====
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final SpeechManager speechManager;
    private final TextView txtInstruction;

    // ===== NAVIGATION DATA =====
    private final ArrayList<DirectionSegment> segments = new ArrayList<>();

    private int pathIndex = 0;
    private int stepCount = 0;
    private int stepsWalkedCount = 0;
    private int currentSegmentTargetSteps = 0;
    private boolean isNavigating = false;
    private double magnitudePrevious = 0;

    private String destination;
    private String currentNode = "Entrance";
    private String lastInstruction = "No instruction to repeat.";

    // ===== CALLBACK =====
    public interface ProgressCallback {
        void onResult(String summary);
    }

    // ===== CONSTRUCTOR =====
    public NavigationManager(Context ctx, TextView txt, SpeechManager sm) {
        txtInstruction = txt;
        speechManager = sm;

        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    // ===== GETTERS =====
    public String getCurrentNode() {
        return currentNode;
    }

    public String getLastInstruction() {
        return lastInstruction;
    }

    // ===== LOAD ROUTE =====
    public void loadRoute(ArrayList<DirectionSegment> route, String dest) {
        segments.clear();
        segments.addAll(route);
        destination = dest;
        stepsWalkedCount = 0;
    }

    // ===== START NAVIGATION =====
    public void start() {
        if (segments.isEmpty()) return;

        isNavigating = true;
        pathIndex = 0;
        stepCount = 0;

        loadSegment();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    // ===== LOAD CURRENT SEGMENT =====
    private void loadSegment() {
        DirectionSegment seg = segments.get(pathIndex);
        currentSegmentTargetSteps = seg.steps;

        String msg = "Walk " + humanize(seg.direction)
                + " for " + seg.steps + " steps towards " + seg.to;

        lastInstruction = msg;
        txtInstruction.setText(msg);
        speechManager.speak(msg);
    }

    // ===== DIRECTION TO SPEECH =====
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

    // ===== STEP DETECTION =====
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isNavigating) return;

        double mag = Math.sqrt(
                event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
        );

        double delta = mag - magnitudePrevious;
        magnitudePrevious = mag;

        if (delta > 2) {
            stepsWalkedCount++;
            stepCount++;

            txtInstruction.setText(
                    "Steps: " + stepCount + " / " + currentSegmentTargetSteps
            );

            if (stepCount >= currentSegmentTargetSteps) {
                advance();
            }
        }
    }

    // ===== MOVE TO NEXT SEGMENT =====
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ===== STOP NAVIGATION =====
    public void stop() {
        isNavigating = false;
        sensorManager.unregisterListener(this);
    }

    // ======================================================
    // ========== AI POWERED PROGRESS SUMMARY =================
    // ======================================================
    public void getProgressSummary(ProgressCallback callback) {
        new Thread(() -> {
            String summary = "You are making good progress.";

            try {
//                summary = "hello";
                DirectionSegment seg = segments.get(pathIndex);

                int totalSteps = 0;
                for (DirectionSegment s : segments) {
                    totalSteps += s.steps;
                }

                int remainingSteps = totalSteps - stepsWalkedCount;

                String atCurrentLocation = "Current Location:";
                if (stepCount != 0)
                    atCurrentLocation = "Last landmark was: ";

                String prompt =
                        "You are an indoor navigation assistant.\n" +
                                "The user is visually impaired.\n" +
                                "Be calm, and concise.\n\n" +
                                atCurrentLocation + seg.from + "\n" +
                                "Next location: " + seg.to + "\n" +
                                "Destination: " + destination + "\n" +
                                "Steps walked: " + stepCount + "\n" +
                                "Steps remaining: " + remainingSteps + "\n\n" +
                                "Generate a spoken progress update.";

                // ---- OpenAI request (same as CommandInterpreter) ----
                JSONObject requestBody = new JSONObject()
                        .put("model", "gpt-4o-mini")
                        .put("temperature", 0.4)
                        .put("messages", new JSONArray()
                                .put(new JSONObject()
                                        .put("role", "user")
                                        .put("content", prompt)));

                HttpURLConnection conn = (HttpURLConnection)
                        new URL(OPENAI_URL).openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes());
                }

                if (conn.getResponseCode() == 200) {
                    String response = new Scanner(conn.getInputStream())
                            .useDelimiter("\\A").next();

                    JSONObject json = new JSONObject(response);
                    summary = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            String finalSummary = summary;
            new Handler(Looper.getMainLooper())
                    .post(() -> callback.onResult(finalSummary));

        }).start();
    }


    private void post(ProgressCallback cb, String text) {
        new Handler(Looper.getMainLooper()).post(() -> cb.onResult(text));
    }

    // ===== MODEL =====
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
