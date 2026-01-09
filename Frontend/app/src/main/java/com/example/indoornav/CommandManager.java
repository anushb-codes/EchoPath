package com.example.indoornav;

import android.content.Context;

import java.util.ArrayList;

public class CommandManager {

    private final SpeechManager speechManager;
    private final NavigationManager navigationManager;

    public CommandManager(SpeechManager sm, NavigationManager nm) {
        speechManager = sm;
        navigationManager = nm;
    }

    public void executeNavigation(
            ArrayList<NavigationManager.DirectionSegment> route,
            String destination
    ) {
        navigationManager.loadRoute(route, destination);
        navigationManager.start();
    }

    public void help(){
        speechManager.speak("Tap on the screen, then speak your command to start. Example \"I want to go to the reception\"");
    }

    public void cancel() {
        navigationManager.stop();
        speechManager.speak("Navigation cancelled");
    }

    public void repeat(String msg) {
        speechManager.speak(msg);
    }
}
