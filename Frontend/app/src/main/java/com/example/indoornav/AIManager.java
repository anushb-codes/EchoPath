//package com.example.indoornav;
//
//import org.json.JSONArray;
//import org.json.JSONObject;
//
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.util.Scanner;
//
//
//public class AIManager {
//    private static final String OPENAI_API_KEY = "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";
//    private static final String OPENAI_URL = "https://models.github.ai/inference/v1/chat/completions";
//
//    public static String result(String text){
//        new Thread(() -> {
//            String rewritten = null;
//            try {
//                String prompt = "Rewrite the following sentence in a " + tone + " tone:\nOriginal: \"" + text + "\"";
//
//                JSONObject body = new JSONObject()
//                        .put("model", "gpt-4o-mini")
//                        .put("messages", new JSONArray()
//                                .put(new JSONObject()
//                                        .put("role", "user")
//                                        .put("content", prompt)))
//                        .put("temperature", 0.7);
//
//                HttpURLConnection conn = (HttpURLConnection) new URL(OPENAI_URL).openConnection();
//                conn.setRequestMethod("POST");
//                conn.setRequestProperty("Authorization", "Bearer " + OPE);
//                conn.setRequestProperty("Content-Type", "application/json");
//                conn.setDoOutput(true);
//
//                try (OutputStream os = conn.getOutputStream()) {
//                    os.write(body.toString().getBytes());
//                }
//
//                if (conn.getResponseCode() == 200) {
//                    String response = new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
//                    JSONObject json = new JSONObject(response);
//                    rewritten = json.getJSONArray("choices")
//                            .getJSONObject(0)
//                            .getJSONObject("message")
//                            .getString("content")
//                            .trim();
//                }
//
//            } catch (Exception e) { e.printStackTrace(); }
//
//            String finalRewritten = rewritten;
//            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalRewritten));
//        }).start();
//    }
//}
