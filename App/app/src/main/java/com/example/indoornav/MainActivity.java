package com.example.indoornav;

import android.os.Bundle;
import android.view.SoundEffectConstants;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.*;
import com.android.volley.toolbox.*;

import org.json.*;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String SERVER_URL = "http://10.0.2.2:5000/navigate";
    private static final String AZURE_KEY = "YOUR_KEY";
    private static final String AZURE_REGION = "YOUR_REGION";

    private TextView txtInstruction;

    private SpeechManager speechManager;
    private NavigationManager navigationManager;
    private CommandManager commandManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtInstruction = findViewById(R.id.txtInstruction);

        speechManager = new SpeechManager(this, AZURE_KEY, AZURE_REGION);
        navigationManager = new NavigationManager(this, txtInstruction, speechManager);
        commandManager = new CommandManager(speechManager, navigationManager);
        speechManager.speak("Tap the screen and speak your command.");

        findViewById(R.id.btnMic).setOnClickListener(v -> {
            v.playSoundEffect(SoundEffectConstants.CLICK);
            txtInstruction.setText("...");
            speechManager.listenOnce(
                    this::handleVoiceInput, // onSuccess
                    () -> speechManager.speak("Can you speak again?") // onError
            );
        });
    }

    //Command Flow
    private void handleVoiceInput(String text) {
        CommandInterpreter.interpretCommand(text, type -> {
            txtInstruction.setText(text);
            runOnUiThread(() -> {
                switch (type) {

                    case NAVIGATE:
                        extractDestination(text);
                        break;

                    case REPEAT:
                        commandManager.repeat(navigationManager.getLastInstruction());
                        break;

                    case CANCEL:
                        commandManager.cancel();
                        break;

                    case STATUS:
                        navigationManager.getProgressSummary(summary -> {
                            txtInstruction.setText(summary);
                            speechManager.speak(summary);
                        });
                        break;

                    case HELP:
                        commandManager.help();
                        break;

                    default:
                        speechManager.speak("Sorry, I did not understand that");
                }
            });
        });
    }

    private void extractDestination(String text) {
        CommandInterpreter.extractDestinationFromText(text, destination -> {
            if (destination == null) {
                speechManager.speak("Please tell me the destination.");
                return;
            }

            sendNavigationRequest(destination);
        });
    }

    //Request to Backend server
    private void sendNavigationRequest(String destination) {

        JSONObject body = new JSONObject();
        try {
            body.put("current_node", navigationManager.getCurrentNode());
            body.put("destination", destination);
        } catch (JSONException ignored) {}

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST, SERVER_URL, body,
                this::handleNavigationResponse,
                e -> speechManager.speak("Server error")
        );

        Volley.newRequestQueue(this).add(req);
    }

    private void handleNavigationResponse(JSONObject response) {
        try {
            String speech = response.getString("speech");
            String destination = response.getString("destination");
            JSONArray path = response.getJSONArray("path");
            JSONArray steps = response.getJSONArray("steps_between_nodes");
            JSONArray dirs = response.getJSONArray("directions_between_nodes");

            ArrayList<NavigationManager.DirectionSegment> segments = new ArrayList<>();

            for (int i = 0; i < steps.length(); i++) {
                segments.add(new NavigationManager.DirectionSegment(
                        path.getString(i),
                        path.getString(i + 1),
                        dirs.getString(i),
                        steps.getInt(i)
                ));
            }

            commandManager.executeNavigation(segments, destination);

        } catch (Exception e) {
            speechManager.speak("Error processing navigation");
        }
    }
}
