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
# 2. MAP WITH DIRECTIONS (EDGE-BASED NAVIGATION)
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
            }
        }
    }
}

# =============================================================================
# 3. CORE LOGIC (SAFE & DETERMINISTIC)
# =============================================================================

def find_shortest_path(graph, start, end):
    queue = [[start]]
    visited = set()

    if start == end:
        return [start]

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

    return None


def generate_directions(path, graph):
    steps = []

    for i in range(len(path) - 1):
        curr = path[i]
        nxt = path[i + 1]
        edge = graph[curr][nxt]

        direction = edge.get("direction", "").lower()
        count = edge.get("steps", 5)  # default to 5 steps if missing

        if direction in ["left", "right", "straight"]:
            steps.append(f"Go {direction} for {count} steps to {nxt}")
        elif direction == "upstairs":
            steps.append(f"Go upstairs for {count} steps to {nxt}")
        elif direction == "downstairs":
            steps.append(f"Go downstairs for {count} steps to {nxt}")
        elif direction in ["back", "backward"]:
            steps.append(f"Turn back and walk {count} steps to {nxt}")
        else:
            steps.append(f"Proceed {count} steps to {nxt}")

    return steps


# =============================================================================
# 4. OPENAI — HUMAN FRIENDLY CONTEXT-AWARE INSTRUCTIONS
# =============================================================================

def humanize_directions(directions, destination):
    prompt = f"""
You are an indoor navigation assistant.

Context:
- The user is visually impaired
- The user is indoors
- The user is walking while holding a phone
- Instructions must be spoken aloud

Rewrite the navigation steps below to be:
- calm
- reassuring
- short spoken sentences
- step-by-step
- easy to follow without vision

Rules:
- Do NOT add or remove steps
- Do NOT change directions or distances
- Do NOT mention maps or screens
- Avoid words like "see", "look", or "over there"

Navigation steps:
{directions}

Destination: {destination}

Return a single spoken paragraph.
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
        print("⚠️ OpenAI failed, using fallback:", e)
        return ". ".join(directions)

# =============================================================================
# 5. AZURE TTS (NON-BLOCKING & SAFE)
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
# 6. API ENDPOINT
# =============================================================================

@app.route("/navigate", methods=["POST"])
def navigate():
    try:
        data = request.json
        voice_text = (data.get("voice_text") or "").lower()
        current_node = data.get("current_node", "Entrance")

        print(f"\n🎧 User said: '{voice_text}' | At: {current_node}")

        map_graph = maps_data["1"]["nodes"]
        map_nodes = list(map_graph.keys())

        # --- Ask AI ONLY to identify destination ---
        prompt = f"""
Map locations: {", ".join(map_nodes)}

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
        print(f"📍 Destination resolved to: {destination}")

        if destination == "UNKNOWN" or destination not in map_nodes:
            msg = "I couldn't find that location. Please try again."
            safe_azure_speak(msg)
            return jsonify({
                "status": "error",
                "speech": msg,
                "path": []
            })

        # --- Navigation logic ---
        path = find_shortest_path(map_graph, current_node, destination)
        raw_directions = generate_directions(path, map_graph)
        speech_text = humanize_directions(raw_directions, destination)

        safe_azure_speak(speech_text)

        response = {
            "status": "success",
            "speech": speech_text,
            "raw_directions": raw_directions,
            "path": path,
            "destination": destination
        }

        print(f"📤 Response: {response}")
        return jsonify(response)

    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "status": "error",
            "speech": "A system error occurred. Please try again.",
            "path": []
        }), 200

# =============================================================================
# 7. RUN SERVER
# =============================================================================

if __name__ == "__main__":
    print("🚀 Indoor Navigation Server Running on Port 5000 updated")
    app.run(host="0.0.0.0", port=5000)
