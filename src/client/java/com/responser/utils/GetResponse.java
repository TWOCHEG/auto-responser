package com.responser.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import com.responser.config.ConfigManager;
import com.responser.utils.Notification;

public class GetResponse {
    public static String getResponse(String userMessage, String username, Map<String, Deque<JsonObject>> chatHistories) {
        ConfigManager cfg = ConfigManager.getInstance();
        try {
            // grab or create a deque holding this user's last messages
            Deque<JsonObject> history = chatHistories.computeIfAbsent(username, u -> new ArrayDeque<>());

            // add the new user message to history
            JsonObject userEntry = new JsonObject();
            userEntry.addProperty("role", "user");
            userEntry.addProperty("content", userMessage);
            history.addLast(userEntry);

            // trim to last 10
            while (history.size() > 10) {
                history.removeFirst();
            }

            // build the payload
            JsonObject payload = new JsonObject();
            payload.addProperty("model", (String) cfg.get("modelId"));
            payload.addProperty("include_reasoning", false);

            JsonArray messagesArray = new JsonArray();
            // include entire history
            for (JsonObject msgObj : history) {
                messagesArray.add(msgObj);
            }
            payload.add("messages", messagesArray);

            String requestBody = new Gson().toJson(payload);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                    .header("Authorization", "Bearer " + cfg.get("apiKey"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject choice = root
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message");
            String assistantText = choice.get("content").getAsString();

            // add assistant's reply to history
            JsonObject assistantEntry = new JsonObject();
            assistantEntry.addProperty("role", "assistant");
            assistantEntry.addProperty("content", assistantText);
            history.addLast(assistantEntry);

            // again trim if needed
            while (history.size() > 10) {
                history.removeFirst();
            }
            return sanitizeMinecraftChat(assistantText);
        } catch (Exception e) {
            Notification.showNotification("error", String.valueOf(e));
            return null;
        }
    }

    public static String sanitizeMinecraftChat(String input) {
        if (input == null) return null;
        String noControl = input.replaceAll("\\p{Cc}", "");
        return noControl.replace('\n', ' ').replace('\r', ' ');
    }
}
