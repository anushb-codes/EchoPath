#include <iostream>
#include <string>
#include <vector>
#include <unordered_map>
#include <unordered_set>
#include <queue>
#include <exception>

// Header-only libraries
#include "httplib.h"
#include "json.hpp"

using json = nlohmann::json;
using namespace std;

// =============================================================================
// 1. DATA STRUCTURES
// =============================================================================

struct Edge {
    string direction;
    int steps;
};

// Represents maps_data["1"]["nodes"]
typedef unordered_map<string, unordered_map<string, Edge>> Graph;

// =============================================================================
// 2. MAP INITIALIZATION
// =============================================================================

Graph init_map_data() {
    Graph graph;
    
    graph["Entrance"]["Reception"] = {"straight", 10};
    graph["Entrance"]["Stairs"] = {"right", 20};
    
    graph["Reception"]["Entrance"] = {"back", 10};
    graph["Reception"]["Reading Room"] = {"left", 15};
    graph["Reception"]["Help Desk"] = {"right", 5};
    
    graph["Reading Room"]["Reception"] = {"back", 15};
    graph["Reading Room"]["Bookshelf A"] = {"left", 8};
    graph["Reading Room"]["Bookshelf B"] = {"right", 8};
    
    graph["Stairs"]["Entrance"] = {"back", 20};
    graph["Stairs"]["2nd Floor"] = {"upstairs", 25};
    
    graph["2nd Floor"]["Stairs"] = {"downstairs", 25};
    
    graph["Help Desk"]["Reception"] = {"back", 5};
    
    graph["Bookshelf A"]["Reading Room"] = {"back", 8};
    graph["Bookshelf B"]["Reading Room"] = {"back", 8};
    
    return graph;
}

// =============================================================================
// 3. PATHFINDING (BFS)
// =============================================================================

vector<string> find_shortest_path(const Graph& graph, const string& start, const string& end) {
    if (start == end) {
        return {start};
    }

    queue<vector<string>> q;
    q.push({start});
    unordered_set<string> visited;

    while (!q.empty()) {
        vector<string> path = q.front();
        q.pop();
        string node = path.back();

        if (visited.find(node) == visited.end()) {
            auto it = graph.find(node);
            if (it != graph.end()) {
                for (const auto& neighbor_pair : it->second) {
                    string neighbor = neighbor_pair.first;
                    vector<string> new_path = path;
                    new_path.push_back(neighbor);
                    
                    if (neighbor == end) {
                        return new_path;
                    }
                    q.push(new_path);
                }
            }
            visited.insert(node);
        }
    }
    return {};
}

// =============================================================================
// 4. DIRECTIONS + STEPS EXTRACTION
// =============================================================================

void generate_directions(const vector<string>& path, const Graph& graph, 
                         vector<int>& steps_out, vector<string>& directions_out) {
    for (size_t i = 0; i < path.size() - 1; ++i) {
        string curr = path[i];
        string nxt = path[i + 1];
        
        Edge edge = graph.at(curr).at(nxt);
        
        // Convert direction to lowercase to match Python's .lower()
        string direction = edge.direction;
        for (char &c : direction) {
            c = tolower(c);
        }
        
        steps_out.push_back(edge.steps);
        directions_out.push_back(direction);
    }
}

// =============================================================================
// 5. SERVER RUNTIME
// =============================================================================

int main() {
    httplib::Server svr;
    Graph graph = init_map_data();

    svr.Post("/navigate", [&graph](const httplib::Request& req, httplib::Response& res) {
        res.set_header("Content-Type", "application/json");
        json response_json;

        try {
            auto data = json::parse(req.body);
            string current_node = data.value("current_node", "Entrance");
            string destination = data.value("destination", "");

            cout << "Current Node: " << current_node << endl;
            cout << "Destination: " << destination << endl;

            if (destination.empty() || graph.find(destination) == graph.end()) {
                response_json["status"] = "error";
                response_json["speech"] = "Invalid destination";
                response_json["path"] = json::array();
                res.status = 200; 
                res.set_content(response_json.dump(), "application/json");
                return;
            }

            vector<string> path = find_shortest_path(graph, current_node, destination);
            vector<int> steps_between_nodes;
            vector<string> directions_between_nodes;
            
            generate_directions(path, graph, steps_between_nodes, directions_between_nodes);

            // Replicate f"Walk through the following nodes: {', '.join(path)}."
            string speech_text = "Walk through the following nodes: ";
            for (size_t i = 0; i < path.size(); ++i) {
                speech_text += path[i];
                if (i < path.size() - 1) {
                    speech_text += ", ";
                } else {
                    speech_text += ".";
                }
            }

            response_json["status"] = "success";
            response_json["speech"] = speech_text;
            response_json["path"] = path;
            response_json["destination"] = destination;
            response_json["steps_between_nodes"] = steps_between_nodes;
            response_json["directions_between_nodes"] = directions_between_nodes;

            cout << "Response: " << speech_text << endl;
            res.status = 200;
            res.set_content(response_json.dump(), "application/json");

        } catch (const exception& e) {
            cerr << "Exception: " << e.what() << endl;
            response_json["status"] = "error";
            response_json["speech"] = "A system error occurred. Please try again.";
            response_json["path"] = json::array();
            res.status = 500;
            res.set_content(response_json.dump(), "application/json");
        }
    });

    cout << "EchoPath Server running on port 5000" << endl;
    svr.listen("0.0.0.0", 5000);

    return 0;
}