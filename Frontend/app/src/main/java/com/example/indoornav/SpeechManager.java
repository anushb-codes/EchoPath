package com.example.indoornav;

import android.content.Context;

import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.widget.TextView;

public class SpeechManager {

    private final String AZURE_KEY;
    private final String AZURE_REGION;

    private SpeechSynthesizer synthesizer;
    private final ExecutorService ttsExecutor = Executors.newSingleThreadExecutor();
//    private final TextView txtInstruction;
    // ---------------- Functional interfaces ----------------
    @FunctionalInterface
    public interface SpeechResultCallback {
        void onResult(String text);
    }

    @FunctionalInterface
    public interface SpeechErrorCallback {
        void onError();
    }

    // ---------------- Constructor ----------------
    public SpeechManager(Context context, String key, String region) {
        AZURE_KEY = key;
        AZURE_REGION = region;
        initTTS();
    }

    // ---------------- Text-to-Speech ----------------
    private void initTTS() {
        ttsExecutor.execute(() -> {
            try {
                SpeechConfig config = SpeechConfig.fromSubscription(AZURE_KEY, AZURE_REGION);
                config.setSpeechSynthesisVoiceName("en-US-AvaMultilingualNeural");
                synthesizer = new SpeechSynthesizer(config, AudioConfig.fromDefaultSpeakerOutput());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void speak(String text) {
        ttsExecutor.execute(() -> {
            try {
                if (synthesizer != null) {
                    synthesizer.SpeakTextAsync(text).get();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ---------------- Speech-to-Text ----------------
    public void listenOnce(SpeechResultCallback onSuccess, SpeechErrorCallback onError) {
        new Thread(() -> {
            try {
                SpeechConfig config = SpeechConfig.fromSubscription(AZURE_KEY, AZURE_REGION);
                config.setSpeechRecognitionLanguage("en-US");

                SpeechRecognizer recognizer =
                        new SpeechRecognizer(config, AudioConfig.fromDefaultMicrophoneInput());

                SpeechRecognitionResult result = recognizer.recognizeOnceAsync().get();
//                txtInstruction.setText(result.getText());
                recognizer.close();

                if (result.getReason() == ResultReason.RecognizedSpeech) {
                    onSuccess.onResult(result.getText());
                } else {
                    onError.onError();
                }
            } catch (Exception e) {
                e.printStackTrace();
                onError.onError();
            }
        }).start();
    }
}
