import os
import traceback
import threading
from flask import Flask, request, jsonify
from openai import OpenAI
import azure.cognitiveservices.speech as speechsdk

# =============================================================================
# 1. SETUP & KEYS
# =============================================================================

app = Flask(__name__)

# --- YOUR KEYS (PRE-FILLED) ---
GITHUB_TOKEN = "YOUR_GITHUB_TOKEN" 
AZURE_SPEECH_KEY = "YOUR_AZURE_KEY1"
AZURE_SPEECH_REGION = "YOUR_REGION"

client = OpenAI(
    base_url="https://models.github.ai/inference",
    api_key=GITHUB_TOKEN,
)

# =============================================================================
# 2. MAP DATA
# =============================================================================

maps_data = {
    "1": {
        "nodes": {
            "Entrance": ["Reception", "Stairs"],
            "Reception": ["Entrance", "Reading Room", "Help Desk"],
            "Stairs": ["Entrance", "2nd Floor"],
            "Reading Room": ["Reception", "Bookshelf A", "Bookshelf B"],
            "Bookshelf A": ["Reading Room"],
            "Bookshelf B": ["Reading Room"],
            "Help Desk": ["Reception"],
            "2nd Floor": ["Stairs"]
        }
    }
}

step_distances = {
    "Entrance-Reception": 10,
    "Reception-Reading Room": 15,
    "Reception-Help Desk": 5,
    "Entrance-Stairs": 20,
    "Stairs-2nd Floor": 25,
    "Reading Room-Bookshelf A": 8,
    "Reading Room-Bookshelf B": 8,
}

# =============================================================================
# 3. SAFETY FUNCTIONS
# =============================================================================

def find_shortest_path(graph, start, end):
    queue = [[start]]
    visited = set()
    if start == end: return [start]
    while queue:
        path = queue.pop(0)
        node = path[-1]
        if node not in visited:
            neighbors = graph.get(node, [])
            for neighbor in neighbors:
                new_path = list(path)
                new_path.append(neighbor)
                if neighbor == end: return new_path
                queue.append(new_path)
            visited.add(node)
    return None

def calculate_total_steps(path):
    total = 0
    if not path: return 0
    for i in range(len(path) - 1):
        key1 = f"{path[i]}-{path[i+1]}"
        key2 = f"{path[i+1]}-{path[i]}"
        total += step_distances.get(key1) or step_distances.get(key2) or 5
    return total

def safe_azure_speak(text):
    """
    Runs Azure speech in a separate thread so it NEVER blocks or crashes the main server.
    """
    def _speak():
        try:
            print(f"   -> 🔊 Attempting Laptop Audio...")
            speech_config = speechsdk.SpeechConfig(subscription=AZURE_SPEECH_KEY, region=AZURE_SPEECH_REGION)
            speech_config.speech_synthesis_voice_name = 'en-US-AvaMultilingualNeural'
            audio_config = speechsdk.audio.AudioOutputConfig(use_default_speaker=True)
            synthesizer = speechsdk.SpeechSynthesizer(speech_config=speech_config, audio_config=audio_config)
            synthesizer.speak_text_async(text) # Fire and forget
        except Exception as e:
            print(f"   -> ⚠️ Laptop Audio Failed (Ignored): {e}")

    # Launch in background thread
    threading.Thread(target=_speak).start()

# =============================================================================
# 4. API ENDPOINT
# =============================================================================

@app.route('/navigate', methods=['POST'])
def navigate():
    # Wrap EVERYTHING in a massive try/catch to ensure JSON is always returned
    try:
        data = request.json
        user_voice_text = (data.get('command') or data.get('voice_text') or data.get('text') or '').lower()
        current_node = data.get('current_node', 'Entrance')

        print(f"\n🎧 Request: '{user_voice_text}' (At: {current_node})")

        # --- STEP 1: AI Decision ---
        try:
            map_nodes = list(maps_data["1"]["nodes"].keys())
            prompt = f"Map nodes: {', '.join(map_nodes)}. User says: '{user_voice_text}'. Return ONLY the exact node name. If unknown, say UNKNOWN."
            
            gpt_res = client.chat.completions.create(
                messages=[{"role": "system", "content": prompt}],
                model="gpt-4o",
                temperature=0
            )
            destination = gpt_res.choices[0].message.content.strip().replace(".", "")
            print(f"📍 Target: {destination}")
        except Exception as ai_error:
            print(f"⚠️ AI Error: {ai_error}")
            return jsonify({"status": "error", "message": "AI Busy", "speech": "AI is busy, try again.", "path": []})

        if destination == "UNKNOWN" or destination not in map_nodes:
            safe_azure_speak("I couldn't find that location.")
            return jsonify({
                "status": "error", 
                "message": "Location not found",
                "speech": "I couldn't find that location.", 
                "path": []
            })

        # --- STEP 2: Logic ---
        path = find_shortest_path(maps_data["1"]["nodes"], current_node, destination)
        total_steps = calculate_total_steps(path)
        instruction = f"Proceed {total_steps} steps to {destination}."
        print(f"🤖 Generated: {instruction}")

        # --- STEP 3: Speak (Safety Sandbox) ---
        safe_azure_speak(instruction)

        # --- STEP 4: Send to Phone ---
        response = {
            "status": "success",
            "speech": instruction,
            "path": path,
            "destination": destination,
            "steps": total_steps
        }
        
        print(f"📤 Sending to App: {response}") 
        return jsonify(response)

    except Exception as e:
        print(f"❌ CRITICAL SERVER ERROR: {e}")
        traceback.print_exc()
        # Even if the server explodes, return a polite JSON to the phone
        return jsonify({
            "status": "error", 
            "message": "Internal Logic Error",
            "speech": "System error occurred.",
            "path": []
        }), 200 # Return 200 OK so Volley doesn't treat it as a network failure

if __name__ == '__main__':
    print("🚀 Server Ready - Listening on 5000...")
    app.run(host='0.0.0.0', port=5000)