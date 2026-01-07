package com.example.indoornav;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
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
    private static final String AZURE_SPEECH_KEY = "key";
    private static final String AZURE_SPEECH_REGION = "eastasia";

    // --- UI ---
    private TextView txtStatus, txtInstruction;
    private Button btnMap1, btnMap2, btnMap3;
    private FloatingActionButton btnMic;

    // --- TTS ---
    private TextToSpeech textToSpeech;

    // --- Navigation Variables ---
    private String currentMapId = "1";
    private String currentNode = "Entrance";
    private ArrayList<String> currentPath = new ArrayList<>();
    private int pathIndex = 0;
    private boolean isNavigating = false;

    // --- Sensor Variables ---
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int stepCount = 0;
    private double magnitudePrevious = 0;
    private static final int STEPS_PER_NODE = 5; // Steps needed to reach next node

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- UI Setup ---
        txtStatus = findViewById(R.id.txtStatus);
        txtInstruction = findViewById(R.id.txtInstruction);
        btnMap1 = findViewById(R.id.btnMap1);
        btnMap2 = findViewById(R.id.btnMap2);
        btnMap3 = findViewById(R.id.btnMap3);
        btnMic = findViewById(R.id.btnMic);

        // --- Sensor Setup ---
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // --- TTS Setup ---
        textToSpeech = new TextToSpeech(this, status -> textToSpeech.setLanguage(Locale.US));

        // --- Map Button Listeners ---
        btnMap1.setOnClickListener(v -> setMap("1", "Library Floor"));
        btnMap2.setOnClickListener(v -> setMap("2", "Computer Dept"));
        btnMap3.setOnClickListener(v -> setMap("3", "Canteen Area"));

        // --- Mic Button Listener (Azure STT) ---
        btnMic.setOnClickListener(v -> startAzureVoiceInput());
    }

    private void setMap(String id, String name) {
        currentMapId = id;
        currentNode = "Entrance";
        txtStatus.setText("Map: " + name);
        isNavigating = false; // Reset navigation
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
                    runOnUiThread(() -> {
                        txtInstruction.setText("You said: " + spokenText);
                        sendToServer(spokenText);
                    });
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
        txtInstruction.setText("Calculating Route...");

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

                        // Parse Path
                        currentPath.clear();
                        for(int i=0; i<pathArray.length(); i++) {
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

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            Toast.makeText(this, "Start Walking! Counting Steps...", Toast.LENGTH_LONG).show();
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

            // Simple step detection
            if (delta > 2) {
                stepCount++;
                txtInstruction.setText("Steps: " + stepCount + " / " + STEPS_PER_NODE);

                if (stepCount >= STEPS_PER_NODE) {
                    advanceToNextNode();
                    stepCount = 0;
                }
            }
        }
    }

    private void advanceToNextNode() {
        pathIndex++;

        if (pathIndex < currentPath.size()) {
            currentNode = currentPath.get(pathIndex);
            String message = "Arrived at " + currentNode + ". Keep walking.";
            if (pathIndex == currentPath.size() - 1) {
                message = "You have arrived at " + currentNode;
                isNavigating = false;
                sensorManager.unregisterListener(this);
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
