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
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.responser.config.ConfigManager;

public class GetResponse {
    public static void getResponse(String userMessage, String username, Map<String, Deque<JsonObject>> chatHistories, Consumer<String> contentConsumer) {
        ConfigManager cfg = ConfigManager.getInstance();
        try {
            Deque<JsonObject> history = chatHistories.computeIfAbsent(username, u -> new ArrayDeque<>());
            // Note: User message is added to history in the caller (response method)

            // Build the payload with streaming enabled
            JsonObject payload = new JsonObject();
            payload.addProperty("model", (String) cfg.get("modelId"));
            payload.addProperty("include_reasoning", false);
            payload.addProperty("stream", true); // Enable streaming
            JsonArray messagesArray = new JsonArray();
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

            // Handle response as a stream of lines
            HttpResponse<Stream<String>> resp = client.send(request, HttpResponse.BodyHandlers.ofLines());
            resp.body().forEach(line -> {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if (!data.equals("[DONE]")) {
                        JsonObject chunk = JsonParser.parseString(data).getAsJsonObject();
                        JsonObject choice = chunk.getAsJsonArray("choices").get(0).getAsJsonObject();
                        if (choice.has("delta") && choice.get("delta").getAsJsonObject().has("content")) {
                            String contentDelta = choice.get("delta").getAsJsonObject().get("content").getAsString();
                            contentConsumer.accept(contentDelta);
                        }
                    }
                    // "[DONE]" indicates the stream has ended; no action needed here
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error processing streaming response: " + e.getMessage(), e);
        }
    }

    public static String sanitizeMinecraftChat(String input) {
        if (input == null) return null;
        String noControl = input.replaceAll("\\p{Cc}", "");
        return noControl.replace('\n', ' ').replace('\r', ' ');
    }
}