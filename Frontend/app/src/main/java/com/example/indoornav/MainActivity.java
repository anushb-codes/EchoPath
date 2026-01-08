package com.example.indoornav;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // ================= CONFIG =================
    private static final String SERVER_URL = "http://10.0.2.2:5000/navigate";
    private static final String AZURE_SPEECH_KEY = "7OsxmGUDzWUN4S7bl0luHskbRSfm3O1VFZpQY2LRoqwtcinDODOGJQQJ99CAAC3pKaRXJ3w3AAAYACOGRSOV";
    private static final String AZURE_SPEECH_REGION = "eastasia";

    // ================= UI =================
    private TextView txtInstruction;
    private FloatingActionButton btnMic;

    // ================= TTS =================
    private TextToSpeech tts;

    // ================= NAVIGATION =================
    private String currentNode = "Entrance";
    private final ArrayList<DirectionSegment> directionSegments = new ArrayList<>();

    private int pathIndex = 0;
    private int stepCount = 0;
    private int currentSegmentTargetSteps = 0;
    private boolean isNavigating = false;

    // ================= SENSORS =================
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private double magnitudePrevious = 0;

    //JSON Data
    String destination;

    // ================= LIFECYCLE =================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtInstruction = findViewById(R.id.txtInstruction);
        btnMic = findViewById(R.id.btnMic);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        tts = new TextToSpeech(this, status -> tts.setLanguage(Locale.US));

        btnMic.setOnClickListener(v -> {
            startAzureVoiceInput();
            tts.speak("How may I help you?", TextToSpeech.QUEUE_FLUSH, null, null);
        });
    }

    // ================= AZURE STT =================
    private void startAzureVoiceInput() {
        txtInstruction.setText("Listening...");

        new Thread(() -> {
            try {
                SpeechConfig config = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SPEECH_REGION);
                config.setSpeechRecognitionLanguage("en-US");

                SpeechRecognizer recognizer =
                        new SpeechRecognizer(config, AudioConfig.fromDefaultMicrophoneInput());

                SpeechRecognitionResult result = recognizer.recognizeOnceAsync().get();
                recognizer.close();

                if (result.getReason() == ResultReason.RecognizedSpeech) {
                    runOnUiThread(() -> sendToServer(result.getText()));
                } else {
                    runOnUiThread(() -> txtInstruction.setText("Could not understand"));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ================= SERVER =================
    private void sendToServer(String voiceText) {
        txtInstruction.setText("Calculating route...");

        JSONObject body = new JSONObject();
        try {
            body.put("voice_text", voiceText);
            body.put("current_node", currentNode);
        } catch (JSONException ignored) {}

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, SERVER_URL, body,
                this::handleServerResponse,
                error -> txtInstruction.setText("Server error")
        );

        request.setRetryPolicy(new DefaultRetryPolicy(30000, 1, 1));
        Volley.newRequestQueue(this).add(request);
    }

    private void handleServerResponse(JSONObject response) {
        try {
            String speech = response.getString("speech");

            directionSegments.clear();

            JSONArray pathArray = response.getJSONArray("path");
            JSONArray stepsArray = response.getJSONArray("steps_between_nodes");
            JSONArray dirsArray = response.getJSONArray("directions_between_nodes");
            destination = response.getString("destination");

            for (int i = 0; i < stepsArray.length(); i++) {
                String from = pathArray.getString(i);
                String to = pathArray.getString(i + 1);
                int steps = stepsArray.getInt(i);
                String dir = dirsArray.getString(i);

                directionSegments.add(
                        new DirectionSegment(from, to, dir, steps)
                );
            }

            txtInstruction.setText(speech);
            tts.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null);

            if (!directionSegments.isEmpty()) {
                startNavigation();
            }

        } catch (JSONException e) {
            e.printStackTrace();
            txtInstruction.setText("Error parsing navigation data");
        }
    }

    // ================= NAVIGATION =================
    private void startNavigation() {
        isNavigating = true;
        pathIndex = 0;
        stepCount = 0;

        loadCurrentSegment();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        Toast.makeText(this, "Start walking", Toast.LENGTH_SHORT).show();
    }

    private void loadCurrentSegment() {
        if (pathIndex >= directionSegments.size()) return;

        DirectionSegment seg = directionSegments.get(pathIndex);
        currentSegmentTargetSteps = seg.steps;

        String instruction = "Walk " + humanizeDirection(seg.direction)
                + " for " + seg.steps + " steps towards " + directionSegments.get(pathIndex).to;

        txtInstruction.setText(instruction);
        tts.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private String humanizeDirection(String dir) {
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

    // ================= SENSOR =================
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
                advanceSegment();
            }
        }
    }

    private void advanceSegment() {
        stepCount = 0;
        pathIndex++;

        if (pathIndex >= directionSegments.size()) {
            isNavigating = false;
            sensorManager.unregisterListener(this);

            String msg = "You have arrived at " + destination;
            txtInstruction.setText(msg);
            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);
            return;
        }

        currentNode = directionSegments.get(pathIndex).from;
        loadCurrentSegment();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    // ================= MODEL =================
    static class DirectionSegment {
        public String from, to, direction;
        int steps;

        DirectionSegment(String f, String t, String d, int s) {
            from = f;
            to = t;
            direction = d;
            steps = s;
        }
    }
}
