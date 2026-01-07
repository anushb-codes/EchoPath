package com.example.indoornav;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
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

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // --- CONFIGURATION ---
    // ✅ NEW URL provided by you
    String SERVER_URL = "https://formable-eryn-unsystematised.ngrok-free.dev/navigate";

    // UI
    private TextView txtStatus, txtInstruction;
    private Button btnMap1, btnMap2, btnMap3;
    private FloatingActionButton btnMic;

    // AI & Logic
    private TextToSpeech textToSpeech;
    private String currentMapId = "1";
    private String currentNode = "Entrance";

    // NAVIGATION VARIABLES
    private ArrayList<String> currentPath = new ArrayList<>();
    private int pathIndex = 0;
    private boolean isNavigating = false;

    // SENSOR VARIABLES (Friend's Logic)
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private int stepCount = 0;
    private static final int STEPS_PER_NODE = 5;

    // ✅ FRIEND'S LOGIC VARIABLE: Just track the last Z value
    private float lastZ = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI Setup
        txtStatus = findViewById(R.id.txtStatus);
        txtInstruction = findViewById(R.id.txtInstruction);

        btnMap1 = findViewById(R.id.btnMap1);
        btnMap2 = findViewById(R.id.btnMap2);
        btnMap3 = findViewById(R.id.btnMap3);
        btnMic = findViewById(R.id.btnMic);

        // Sensor Setup
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // TTS Setup
        textToSpeech = new TextToSpeech(this, status -> textToSpeech.setLanguage(Locale.US));

        // Listeners
        btnMap1.setOnClickListener(v -> setMap("1", "Library Floor"));
        btnMap2.setOnClickListener(v -> setMap("2", "Computer Dept"));
        btnMap3.setOnClickListener(v -> setMap("3", "Canteen Area"));
        btnMic.setOnClickListener(v -> startVoiceInput());
    }

    private void setMap(String id, String name) {
        currentMapId = id;
        currentNode = "Entrance";
        txtStatus.setText("Map: " + name);
        isNavigating = false;
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Where to?");
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            String spokenText = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            txtInstruction.setText("You said: " + spokenText);
            sendToServer(spokenText);
        }
    }

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

                        currentPath.clear();
                        for(int i=0; i<pathArray.length(); i++) {
                            currentPath.add(pathArray.getString(i));
                        }

                        if (currentPath.size() > 1) {
                            startNavigation();
                        }

                        txtInstruction.setText(speech);
                        textToSpeech.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null);

                    } catch (JSONException e) {
                        txtInstruction.setText("Error: No path found");
                    }
                },
                error -> {
                    error.printStackTrace();
                    txtInstruction.setText("Server Error (Check Internet)");
                }
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
            Toast.makeText(this, "Start Walking!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isNavigating && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            // ✅ FRIEND'S LOGIC (Extracted from SensorController.java)
            // It is much simpler: Just check Z-Axis changes.
            float z = event.values[2];

            // Check if the change in Z is greater than 3 (Simple Threshold)
            if (Math.abs(z - lastZ) > 3) {
                stepCount++;

                // Update UI
                txtInstruction.setText("Steps: " + stepCount + " / " + STEPS_PER_NODE);

                // Check Navigation
                if (stepCount >= STEPS_PER_NODE) {
                    advanceToNextNode();
                    stepCount = 0;
                }
            }
            lastZ = z; // Update for next loop
        }
    }

    private void advanceToNextNode() {
        pathIndex++;

        if (pathIndex < currentPath.size()) {
            String nextNode = currentPath.get(pathIndex);
            currentNode = nextNode;

            String message = "Arrived at " + nextNode + ". Keep walking.";
            if (pathIndex == currentPath.size() - 1) {
                message = "You have arrived at " + nextNode;
                isNavigating = false;
                sensorManager.unregisterListener(this);
            }

            txtInstruction.setText(message);
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}