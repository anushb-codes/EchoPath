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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // --- CONFIGURATION ---
    private static final String SERVER_URL = "http://10.0.2.2:5000/navigate";
    private static final String AZURE_SPEECH_KEY = "7OsxmGUDzWUN4S7bl0luHskbRSfm3O1VFZpQY2LRoqwtcinDODOGJQQJ99CAAC3pKaRXJ3w3AAAYACOGRSOV";
    private static final String AZURE_SPEECH_REGION = "eastasia";

    // --- UI ---
    private TextView txtStatus, txtInstruction;
    private FloatingActionButton btnMic;

    // --- TTS ---
    private TextToSpeech textToSpeech;

    // --- Navigation Variables ---
    private String currentMapId = "1";
    private String currentNode = "Entrance";
    private ArrayList<String> currentPath = new ArrayList<>();
    private int pathIndex = 0;
    private boolean isNavigating = false;

    // --- Step/Direction Variables ---
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int stepCount = 0;
    private double magnitudePrevious = 0;

    // Server-provided steps & directions
    private JSONArray rawDirections = new JSONArray();
    private int currentNodeStepTarget = 5; // Default fallback

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- UI Setup ---
        txtStatus = findViewById(R.id.txtStatus);
        txtInstruction = findViewById(R.id.txtInstruction);
        btnMic = findViewById(R.id.btnMic);

        // --- Sensor Setup ---
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // --- TTS Setup ---
        textToSpeech = new TextToSpeech(this, status -> textToSpeech.setLanguage(Locale.US));

        // --- Mic Button Listener ---
        btnMic.setOnClickListener(v -> startAzureVoiceInput());

        // Initialize map
        setMap("1", "Library Floor");
    }

    private void setMap(String id, String name) {
        currentMapId = id;
        currentNode = "Entrance";
        currentPath.clear();
        pathIndex = 0;
        isNavigating = false;
        rawDirections = new JSONArray();
        txtStatus.setText("Map: " + name);
        txtInstruction.setText("Ready to navigate.");
    }

    // --- AZURE SPEECH TO TEXT ---
    private void startAzureVoiceInput() {
        txtInstruction.setText("Listening...");
        new Thread(() -> {
            try {
                SpeechConfig speechConfig = SpeechConfig.fromSubscription(AZURE_SPEECH_KEY, AZURE_SPEECH_REGION);
                speechConfig.setSpeechRecognitionLanguage("en-US");

                AudioConfig audioConfig = AudioConfig.fromDefaultMicrophoneInput();
                SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig);

                // Recognize speech once
                SpeechRecognitionResult result = recognizer.recognizeOnceAsync().get();

                if (result.getReason() == ResultReason.RecognizedSpeech) {
                    String spokenText = result.getText();
                    runOnUiThread(() -> txtInstruction.setText("You said: " + spokenText));
                    sendToServer(spokenText);
                } else {
                    runOnUiThread(() -> txtInstruction.setText("Could not recognize speech."));
                }

                recognizer.close();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> txtInstruction.setText("Error in Azure STT"));
            }
        }).start();
    }

    // --- SEND VOICE INPUT TO SERVER ---
    private void sendToServer(String voiceText) {
        txtInstruction.setText("Calculating route...");

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("map_id", currentMapId);
            jsonBody.put("current_node", currentNode);
            jsonBody.put("voice_text", voiceText);
        } catch (JSONException e) { e.printStackTrace(); }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SERVER_URL, jsonBody,
                response -> {
                    try {
                        String speech = response.getString("speech");
                        JSONArray pathArray = response.getJSONArray("path");
                        rawDirections = response.optJSONArray("raw_directions");

                        // Parse path
                        currentPath.clear();
                        for (int i = 0; i < pathArray.length(); i++) {
                            currentPath.add(pathArray.getString(i));
                        }

                        if (currentPath.size() > 1) startNavigation();

                        txtInstruction.setText(speech);
                        textToSpeech.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null);

                    } catch (JSONException e) {
                        txtInstruction.setText("Error: No path found");
                    }
                },
                error -> txtInstruction.setText("Server Error")
        );

        request.setRetryPolicy(new DefaultRetryPolicy(
                30000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(this).add(request);
    }

    // --- NAVIGATION LOGIC ---
    private void startNavigation() {
        isNavigating = true;
        pathIndex = 0;
        stepCount = 0;

        // Set first node's step target
        currentNodeStepTarget = getStepsForCurrentSegment();

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            Toast.makeText(this, "Start walking! Counting steps...", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isNavigating && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            double magnitude = Math.sqrt(x*x + y*y + z*z);
            double delta = magnitude - magnitudePrevious;
            magnitudePrevious = magnitude;

            // Step detection threshold
            if (delta > 2) {
                stepCount++;
                txtInstruction.setText("Steps: " + stepCount + " / " + currentNodeStepTarget);

                if (stepCount >= currentNodeStepTarget) {
                    advanceToNextNode();
                    stepCount = 0;
                    currentNodeStepTarget = getStepsForCurrentSegment();
                }
            }
        }
    }

    private int getStepsForCurrentSegment() {
        if (rawDirections == null) return 5;
        if (pathIndex >= rawDirections.length()) return 5;

        try {
            JSONObject segment = rawDirections.getJSONObject(pathIndex);
            return segment.optInt("steps", 5); // Fallback to 5
        } catch (JSONException e) {
            return 5;
        }
    }

    private String getDirectionForCurrentSegment() {
        if (rawDirections == null) return "";
        if (pathIndex >= rawDirections.length()) return "";

        try {
            JSONObject segment = rawDirections.getJSONObject(pathIndex);
            return segment.optString("direction", "");
        } catch (JSONException e) {
            return "";
        }
    }

    private void advanceToNextNode() {
        pathIndex++;

        if (pathIndex < currentPath.size()) {
            currentNode = currentPath.get(pathIndex);

            // Get direction for context-aware instruction
            String direction = getDirectionForCurrentSegment();
            String message;
            if (pathIndex == currentPath.size() - 1) {
                message = "You have arrived at " + currentNode + ".";
                isNavigating = false;
                sensorManager.unregisterListener(this);
            } else {
                message = "Walk " + direction + " to " + currentNode + ".";
            }

            txtInstruction.setText(message);
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}
