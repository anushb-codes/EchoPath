# EchoPath: Indoor Navigation Assistant for the Visually Impaired

## Overview

EchoPath is a voice‑guided indoor navigation system designed to assist users especially visually impaired individuals in navigating complex indoor spaces such as libraries, academic buildings, and offices. The app listens to spoken commands, understands user intent using AI, calculates routes, and provides step‑by‑step audio instructions with real‑time progress updates.

The goal of EchoPath is to make indoor spaces more accessible by removing the dependency on visual maps or constant human assistance.

---

## Problem Statement

Outdoor navigation is well‑supported by GPS‑based tools, but **indoor navigation remains a major challenge**, particularly for:

* Visually impaired users
* First‑time visitors to large buildings
* Users navigating unfamiliar or crowded indoor environments

Key challenges include:

* Lack of GPS availability indoors
* Difficulty understanding floor layouts
* Inaccessibility of visual signboards

EchoPath solves this by combining **voice interaction, AI‑based intent understanding, and sensor‑based step tracking** to guide users safely and confidently indoors.

---

## Key Features

* Voice command input ("Take me to the Reading Room")
* AI‑based command interpretation (navigate, repeat, cancel, status)
* Destination extraction from natural language
* Step‑based navigation using device sensors
* Spoken turn‑by‑turn instructions
* Real‑time progress summaries with reassuring feedback

---

## Tech Stack

### Mobile Application/ AI Layer

* **Language:** Java (Standard Android)
* **Platform:** Android
* **Core Components:**

  * Mobile sensor input
  * STT for user commands, TTS for instruction output (Azure Speech)
  * Command classification, Destination extraction, Natural language response generation (Azure OpenAI)

### Backend

* **Language:** Python
* **Framework:** Flask
* **Responsibilities:**
  * Store indoor map graph model
  * Rout calculation
  * State management


---

## Project Directory Structure

```
EchoPath/
│
├── App/
│   ├── app/
│   │   ├── src/main/java/com/example/indoornav/
│   │   │   ├── MainActivity.java
│   │   │   ├── NavigationManager.java
│   │   │   ├── SpeechManager.java
│   │   │   ├── CommandInterpreter.java
│   │   │   └── CommandManager.java
│   │   └── res/
│   └── build.gradle
│
├── Backend/
│   └── server.py
│
└── README.md
```

---

## How to Run the Project Locally

### Backend (Python Computation Engine)

#### Prerequisites

* Python 3.9+

#### Steps

```bash
cd EchoPath/Backend
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install flask
```

Set environment variables:

<!-- ```bash
AZURE_OPENAI_API_KEY=your_key
AZURE_OPENAI_ENDPOINT=your_endpoint
```-->

Run the server:

```bash
python server.py
```

The backend will start locally (e.g., `http://localhost:5000`).

---

### Android App (Frontend + AI Layer)

#### Prerequisites

* Android Studio
* Android device or emulator (with sensors enabled)
* Azure AI Services key

#### Steps

1. Open `EchoPath/App` in Android Studio
2. Sync Gradle dependencies
3. Update backend URL in the app (if needed)
4. Run the app on a physical device (recommended for sensor accuracy) or Android Emulator

---

## Future Enhancements

* Efficient mapping via floor plan analysis using Azure ML Real-time environment mapping using Azure Computer Vision (SLAM).
* Multi-language support for broader accessibility using Azure Language
* Real-time obstacle detection with automatic rerouting
* Wrong-turn detection and instant rerouting
* Contextual memory for personalized instructions


---

## Accessibility Focus

EchoPath is built with accessibility as a core principle:

* Hands‑lights, app-based navigation
* Minimalistic UI to reduce cognitive load
* Clear, reassuring voice-based commands
* Haptic/audio feedback on the mic button

---

## License

This project is developed for academic and educational purposes.

---

**EchoPath - Guiding the visually impaired, step by step.**
