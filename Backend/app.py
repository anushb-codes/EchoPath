import traceback
from flask import Flask, request, jsonify

# =============================================================================
# 1. APP SETUP
# =============================================================================

app = Flask(__name__)

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
# 4. DIRECTIONS + STEPS EXTRACTION
# =============================================================================

def generate_directions(path, graph):
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

    return steps_between_nodes, directions_between_nodes

# =============================================================================
# 5. API ENDPOINT
# =============================================================================

@app.route("/navigate", methods=["POST"])
def navigate():
    try:
        data = request.json or {}
        current_node = data.get("current_node", "Entrance")
        destination = data.get("destination")  # Android app will send this
        print("Current Node: ", current_node)
        print("Destination: ", destination)

        graph = maps_data["1"]["nodes"]
        nodes = list(graph.keys())

        if not destination or destination not in nodes:
            msg = "Invalid destination"
            return jsonify({
                "status": "error",
                "speech": msg,
                "path": []
            })

        path = find_shortest_path(graph, current_node, destination)
        steps_between_nodes, directions_between_nodes = generate_directions(path, graph)

        # Generate a simple speech-friendly message (Android app can replace with TTS)
        speech_text = f"Walk through the following nodes: {', '.join(path)}."

        response = {
            "status": "success",
            "speech": speech_text,
            "path": path,
            "destination": destination,
            "steps_between_nodes": steps_between_nodes,
            "directions_between_nodes": directions_between_nodes
        }
        print("Response: ", speech_text)
        return jsonify(response)

    except Exception:
        traceback.print_exc()
        return jsonify({
            "status": "error",
            "speech": "A system error occurred. Please try again.",
            "path": []
        }), 500

# =============================================================================
# 6. RUN SERVER
# =============================================================================

if __name__ == "__main__":
    print("🚀 Indoor Navigation Server running on port 5000")
    app.run(host="0.0.0.0", port=5000)
