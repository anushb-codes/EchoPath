package com.example.indoornav;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * CommandInterpreter handles:
 * 1️⃣ Classifying user commands into NAVIGATE, REPEAT, CANCEL, STATUS, UNKNOWN
 * 2️⃣ Extracting the destination from user commands (if any)
 */
public class CommandInterpreter {

    // ⚠️ Use secure storage in real apps
    private static final String OPENAI_API_KEY = "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";
    private static final String OPENAI_URL = "https://models.github.ai/inference/v1/chat/completions";

    // ================= COMMAND TYPE =================
    public enum CommandType {
        NAVIGATE,
        REPEAT,
        CANCEL,
        STATUS,
        UNKNOWN
    }

    // ================= CALLBACKS =================
    public interface Callback {
        void onResult(CommandType type);
    }

    public interface DestinationCallback {
        void onResult(String destination);
    }

    // ================= INTERPRET COMMAND =================
    public static void interpretCommand(final String userText, final Callback callback) {
        new Thread(() -> {
            CommandType type = CommandType.UNKNOWN;

            try {
                String prompt = "You are a command classifier for a voice-based indoor navigation app.\n" +
                        "Classify the user's command into exactly ONE of the following words:\n" +
                        "NAVIGATE, REPEAT, CANCEL, STATUS, UNKNOWN\n" +
                        "Rules:\n" +
                        "- NAVIGATE: user wants to go somewhere\n" +
                        "- REPEAT: user asks to repeat instructions\n" +
                        "- CANCEL: user wants to stop navigation\n" +
                        "- STATUS: user asks progress, location, or distance\n" +
                        "- UNKNOWN: none of the above\n" +
                        "Return ONLY one word, exactly one of: NAVIGATE, REPEAT, CANCEL, STATUS, UNKNOWN.\n" +
                        "User command:\n" + userText;

                JSONObject requestBody = new JSONObject()
                        .put("model", "gpt-4o-mini")
                        .put("temperature", 0)
                        .put("messages", new JSONArray()
                                .put(new JSONObject()
                                        .put("role", "user")
                                        .put("content", prompt)));


                HttpURLConnection conn = (HttpURLConnection)
                        new URL(OPENAI_URL).openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes());
                }

                if (conn.getResponseCode() == 200) {
                    String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    JSONObject json = new JSONObject(response);
                    String rawResult = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();

                    // Map output using contains() for robustness
                    String r = rawResult.toUpperCase();
                    if (r.contains("NAVIGATE")) type = CommandType.NAVIGATE;
                    else if (r.contains("REPEAT")) type = CommandType.REPEAT;
                    else if (r.contains("CANCEL")) type = CommandType.CANCEL;
                    else if (r.contains("STATUS")) type = CommandType.STATUS;
                    else type = CommandType.UNKNOWN;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            CommandType finalType = type;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalType));
        }).start();
    }

    // ================= EXTRACT DESTINATION =================
    public static void extractDestinationFromText(final String userText, final DestinationCallback callback) {
        new Thread(() -> {
            String destination = null;

            try {
                String[] validLocations = new String[]{
                        "Entrance", "Reception", "Reading Room", "Stairs",
                        "2nd Floor", "Help Desk", "Bookshelf A", "Bookshelf B"
                };

                String locationsStr = String.join(", ", validLocations);

                String prompt = "You are a voice assistant for an indoor navigation app.\n" +
                        "Extract the destination from the user's command.\n" +
                        "Only return ONE of the valid locations if mentioned. Valid locations: " +
                        locationsStr + "\n" +
                        "User command:\n" + userText;

                JSONObject requestBody = new JSONObject()
                        .put("model", "gpt-4o-mini")
                        .put("temperature", 0)
                        .put("messages", new JSONArray()
                                .put(new JSONObject()
                                        .put("role", "user")
                                        .put("content", prompt)));

                HttpURLConnection conn = (HttpURLConnection)
                        new URL(OPENAI_URL).openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.toString().getBytes());
                }

                if (conn.getResponseCode() == 200) {
                    String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
                    JSONObject json = new JSONObject(response);
                    String rawResult = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim();

                    // Match against valid locations using contains (case-insensitive)
                    for (String loc : validLocations) {
                        if (rawResult.toLowerCase().contains(loc.toLowerCase())) {
                            destination = loc;
                            break;
                        }
                    }

                    System.out.println("Extracted destination: " + destination + " | raw: " + rawResult);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            String finalDestination = destination;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalDestination));
        }).start();
    }
}
