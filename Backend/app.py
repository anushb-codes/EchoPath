import traceback
import threading
from flask import Flask, request, jsonify
from openai import OpenAI
import azure.cognitiveservices.speech as speechsdk

# =============================================================================
# 1. APP & CLIENT SETUP
# =============================================================================

app = Flask(__name__)

# ❗ DO NOT HARDCODE KEYS IN REAL PROJECTS
GITHUB_TOKEN = "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"
AZURE_SPEECH_KEY = "7OsxmGUDzWUN4S7bl0luHskbRSfm3O1VFZpQY2LRoqwtcinDODOGJQQJ99CAAC3pKaRXJ3w3AAAYACOGRSOV"
AZURE_SPEECH_REGION = "eastasia"

client = OpenAI(
    base_url="https://models.github.ai/inference",
    api_key=GITHUB_TOKEN,
)

# =============================================================================
# 2. MAP WITH EDGE-BASED DIRECTIONS
# =============================================================================

maps_data = {
    "1": {
        "nodes": {
            "Entrance": {
                "Reception": {"direction": "straight", "steps": 10},
                "Stairs": {"direction": "right", "steps": 20}
            },
            "Reception": {
                "Entrance": {"direction": "back", "steps": 10},
                "Reading Room": {"direction": "left", "steps": 15},
                "Help Desk": {"direction": "right", "steps": 5}
            },
            "Reading Room": {
                "Reception": {"direction": "back", "steps": 15},
                "Bookshelf A": {"direction": "left", "steps": 8},
                "Bookshelf B": {"direction": "right", "steps": 8}
            },
            "Stairs": {
                "Entrance": {"direction": "back", "steps": 20},
                "2nd Floor": {"direction": "upstairs", "steps": 25}
            },
            "2nd Floor": {
                "Stairs": {"direction": "downstairs", "steps": 25}
            },
            "Help Desk": {
                "Reception": {"direction": "back", "steps": 5}
            },

            "Bookshelf A": {
                "Reading Room": {"direction": "back", "steps": 8}
            },

            "Bookshelf B": {
                "Reading Room": {"direction": "back", "steps": 8}
            }

        }
    }
}

# =============================================================================
# 3. PATHFINDING (BFS)
# =============================================================================

def find_shortest_path(graph, start, end):
    if start == end:
        return [start]

    queue = [[start]]
    visited = set()

    while queue:
        path = queue.pop(0)
        node = path[-1]

        if node not in visited:
            for neighbor in graph.get(node, {}):
                new_path = path + [neighbor]
                if neighbor == end:
                    return new_path
                queue.append(new_path)
            visited.add(node)

    return []

# =============================================================================
# 4. DIRECTION + STEP EXTRACTION (CRITICAL FIX)
# =============================================================================

def generate_directions(path, graph):
    spoken_steps = []
    steps_between_nodes = []
    directions_between_nodes = []

    for i in range(len(path) - 1):
        curr = path[i]
        nxt = path[i + 1]
        edge = graph[curr][nxt]

        direction = edge.get("direction", "straight").lower()
        steps = edge.get("steps", 5)

        steps_between_nodes.append(steps)
        directions_between_nodes.append(direction)

        if direction in ["left", "right", "straight"]:
            spoken_steps.append(f"Go {direction} for {steps} steps to {nxt}")
        elif direction == "upstairs":
            spoken_steps.append(f"Go upstairs for {steps} steps to {nxt}")
        elif direction == "downstairs":
            spoken_steps.append(f"Go downstairs for {steps} steps to {nxt}")
        elif direction in ["back", "backward"]:
            spoken_steps.append(f"Turn back and walk {steps} steps to {nxt}")
        else:
            spoken_steps.append(f"Proceed {steps} steps to {nxt}")

    return spoken_steps, steps_between_nodes, directions_between_nodes

# =============================================================================
# 5. CONTEXT-AWARE HUMAN FRIENDLY INSTRUCTIONS
# =============================================================================

def humanize_directions(directions, destination):
    prompt = f"""
You are an indoor navigation assistant.

Context:
- User is visually impaired
- User is indoors
- User is walking with a phone
- Instructions will be spoken aloud

Rules:
- Keep sentences short
- Be calm and reassuring
- Do NOT add or remove steps
- Do NOT change directions
- Avoid words like "see" or "look"

Navigation steps:
{directions}

Destination: {destination}

Return ONE spoken paragraph.
"""

    try:
        response = client.chat.completions.create(
            model="gpt-4o",
            messages=[
                {"role": "system", "content": "You generate safe spoken navigation instructions."},
                {"role": "user", "content": prompt}
            ],
            temperature=0.4
        )
        return response.choices[0].message.content.strip()

    except Exception as e:
        print("⚠️ OpenAI failed, fallback used:", e)
        return ". ".join(directions)

# =============================================================================
# 6. AZURE TTS (NON-BLOCKING)
# =============================================================================

def safe_azure_speak(text):
    def _speak():
        try:
            speech_config = speechsdk.SpeechConfig(
                subscription=AZURE_SPEECH_KEY,
                region=AZURE_SPEECH_REGION
            )
            speech_config.speech_synthesis_voice_name = "en-US-AvaMultilingualNeural"

            audio_config = speechsdk.audio.AudioOutputConfig(use_default_speaker=True)
            synthesizer = speechsdk.SpeechSynthesizer(
                speech_config=speech_config,
                audio_config=audio_config
            )
            synthesizer.speak_text_async(text)
        except Exception as e:
            print("⚠️ Azure TTS failed:", e)

    threading.Thread(target=_speak, daemon=True).start()

# =============================================================================
# 7. API ENDPOINT
# =============================================================================

@app.route("/navigate", methods=["POST"])
def navigate():
    try:
        data = request.json or {}
        voice_text = (data.get("voice_text") or "").lower()
        current_node = data.get("current_node", "Entrance")

        print(f"\n🎧 User said: '{voice_text}' | At: {current_node}")

        graph = maps_data["1"]["nodes"]
        nodes = list(graph.keys())

        # --- DESTINATION EXTRACTION (AI ONLY FOR NAME) ---
        prompt = f"""
Map locations: {", ".join(nodes)}

User said: "{voice_text}"

Return ONLY the exact location name.
If unknown, return UNKNOWN.
"""

        ai_res = client.chat.completions.create(
            model="gpt-4o",
            messages=[{"role": "system", "content": prompt}],
            temperature=0
        )

        destination = ai_res.choices[0].message.content.strip().replace(".", "")
        print(f"📍 Destination: {destination}")

        if destination == "UNKNOWN" or destination not in nodes:
            msg = "I couldn't find that location. Please try again."
            safe_azure_speak(msg)
            return jsonify({
                "status": "error",
                "speech": msg,
                "path": []
            })

        # --- PATH + DIRECTIONS ---
        path = find_shortest_path(graph, current_node, destination)
        spoken_steps, steps_between_nodes, directions_between_nodes = generate_directions(path, graph)
        speech_text = humanize_directions(spoken_steps, destination)

        safe_azure_speak(speech_text)

        response = {
            "status": "success",
            "speech": speech_text,
            "path": path,
            "destination": destination,
            "steps_between_nodes": steps_between_nodes,
            "directions_between_nodes": directions_between_nodes
        }

        print("📤 Response:", response)
        return jsonify(response)

    except Exception:
        traceback.print_exc()
        return jsonify({
            "status": "error",
            "speech": "A system error occurred. Please try again.",
            "path": []
        }), 200

# =============================================================================
# 8. RUN SERVER
# =============================================================================

if __name__ == "__main__":
    print("🚀 Indoor Navigation Server running on port 5000")
    app.run(host="0.0.0.0", port=5000)
