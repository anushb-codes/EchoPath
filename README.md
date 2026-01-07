# 📍 EchoPath: Intelligent Indoor Navigation System

> **"Google Maps for Indoors"** – A voice-activated navigation assistant for complex indoor infrastructures where GPS fails.

## 🚀 Overview
EchoPath is a native Android application that utilizes **Generative AI (GPT-4o)** for natural language understanding and **Dead Reckoning (Sensor Fusion)** for real-time indoor positioning. It bridges the gap in "last-mile" navigation for libraries, hospitals, and campuses.

## 🛠️ Tech Stack
* **Frontend:** Android (Java), XML, Volley (Networking), SensorManager (Accelerometers).
* **Backend:** Python (Flask), Ngrok (Tunneling).
* **AI & NLP:** GitHub Models (GPT-4o), Azure Cognitive Services (Speech-to-Text/Text-to-Speech).
* **Architecture:** Client-Server Model with JSON communication.

## 🌟 Key Features
* **🗣️ Natural Voice Commands:** "Take me to the library" (No need to type exact room numbers).
* **👣 Pedometer Navigation:** Custom step-detection algorithm filters noise and tracks user movement without GPS.
* **🧠 "Cheat Mode" Fallback:** Hybrid system that switches to keyword logic if AI services are unreachable.
* **⚡ Latency Optimized:** Implemented threading and async calls to ensure <2s response time on mobile networks.

## 📸 Demo
*(You can add a screenshot of your app here later)*

## 🔧 Installation & Setup

### Backend (Server)
1. Navigate to the backend folder:
   ```bash
   cd Backend