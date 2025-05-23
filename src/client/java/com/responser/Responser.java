package com.responser;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.text.Text;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.glfw.GLFW;

import com.mojang.authlib.GameProfile;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.text.MessageFormat;

import com.google.gson.JsonObject;

import com.responser.utils.MentionChecker;
import com.responser.utils.GetResponse;
import com.responser.utils.Notification;
import com.responser.config.ConfigManager;

public class Responser implements ClientModInitializer {
	private static final Map<String, Deque<JsonObject>> chatHistories = new HashMap<>();

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private ScheduledFuture<?> scheduledTask = null;
	private boolean cancelTask = false;

	@Override
	public void onInitializeClient() {
		KeyBinding customKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"Cancel Generation",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_ENTER,
			"Auto Responser"
		));
		// Очистка chatHistories при отключении от сервера
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			chatHistories.clear();
		});

		// обычные сообщения
		ClientReceiveMessageEvents.CHAT.register(
			(Text message, SignedMessage signedMsg, GameProfile sender, Parameters params, Instant timestamp) -> {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client.player == null) return;
				if (sender.getName().equals(client.getSession().getUsername())) return;

				ConfigManager cfg = ConfigManager.getInstance();
				boolean enable = cfg.get("enable");
				if (!enable) return;

				processMessage(message.getString(), sender.getName(), client, customKeyBinding, cfg);
			}
		);
		// для серверов с плагинами на чат
		ClientReceiveMessageEvents.GAME.register(
			(Text message, boolean overlay) -> {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client.player == null) return;

				String text = message.getString();
				String senderName = extractSenderName(text);

				if (senderName == null || senderName.equals(client.getSession().getUsername())) return;

				ConfigManager cfg = ConfigManager.getInstance();
				boolean enable = cfg.get("enable");
				if (!enable) return;

				processMessage(message.getString(), senderName, client, customKeyBinding, cfg);
			}
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || scheduledTask == null || scheduledTask.isDone()) return;

			// Отмена по открытию чата или по кастомной клавише
			if (client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen || customKeyBinding.wasPressed()) {
				cancelTask = true;
				scheduledTask.cancel(false);
				resetFlags();
				Notification.showNotification("canceled", "generation canceled");
			}
		});
	}

	private void scheduleMessageProcessing(String senderName, String text, MinecraftClient client, String prefix, ConfigManager cfg) {
		if (scheduledTask != null && !scheduledTask.isDone()) {
			scheduledTask.cancel(false);
		}

		resetFlags();

		scheduledTask = scheduler.schedule(() -> {
			if (!cancelTask) {
				client.execute(() -> response(senderName, text, prefix, client, cfg));
			}
			resetFlags();
		}, ((Number) cfg.get("delayS")).longValue(), TimeUnit.SECONDS);
	}

	private void resetFlags() {
		cancelTask = false;
	}

	private void processMessage(String text, String senderName, MinecraftClient client, KeyBinding customKeyBinding, ConfigManager cfg) {
		String playerName = client.getSession().getUsername();
		String cleanedText = null;
		String prefix = "";

		if (text.contains("[private]") || text.contains("whispers to you")) {
			int colonIndex = text.indexOf(": ");
			if (colonIndex != -1) {
				cleanedText = text.substring(colonIndex + 2).trim();
			} else {
				String senderPrefix = "<" + senderName + "> ";
				int senderIndex = text.indexOf(senderPrefix);
				if (senderIndex != -1) {
					cleanedText = text.substring(senderIndex + senderPrefix.length()).trim();
				} else {
					int privateIndex = text.indexOf("[private]");
					if (privateIndex != -1) {
						cleanedText = text.substring(privateIndex + "[private]".length()).trim();
						cleanedText = cleanedText.replaceAll("^<[^>]+>\\s*", "").trim();
					} else {
						cleanedText = text.trim();
					}
				}
			}
			prefix = "/tell " + senderName + " ";
		} else {
			String messagePart;
			int colonIndex = text.indexOf(": ");
			if (colonIndex != -1) {
				messagePart = text.substring(colonIndex + 2).trim();
			} else {
				String senderPrefix = "<" + senderName + "> ";
				if (text.startsWith(senderPrefix)) {
					messagePart = text.substring(senderPrefix.length()).trim();
				} else {
					messagePart = text.trim();
				}
			}

			String mention = MentionChecker.checkMention(messagePart, playerName);
			if (mention != null) {
				cleanedText = messagePart.replaceAll("(?i)" + Pattern.quote(mention), "").trim();
				String prefixPart = colonIndex != -1 ? text.substring(0, colonIndex) : text;
				if (prefixPart.contains("[clan]")) {
					prefix = "#";
				} else if (prefixPart.contains("[global]")) {
					prefix = "!";
				} else {
					prefix = "";
				}
			}
		}

		if (cleanedText != null) {
			Notification.showNotification(
					"mentioned!",
					MessageFormat.format(
							"gen will start in {0}s, press {1} or open the chat to cancel", cfg.get("delayS"), I18n.translate(customKeyBinding.getBoundKeyTranslationKey()).toUpperCase()
					)
			);

			scheduleMessageProcessing(senderName, cleanedText, client, prefix, cfg);
		}
	}

	public static void response(String senderName, String text, String prefix, MinecraftClient client, ConfigManager cfg) {
		new Thread(() -> {
			try {
				Deque<JsonObject> history = chatHistories.computeIfAbsent(senderName, u -> new ArrayDeque<>());
				JsonObject userEntry = new JsonObject();
				userEntry.addProperty("role", "user");
				userEntry.addProperty("content", text);
				history.addLast(userEntry);

				StringBuilder fullResponse = new StringBuilder();
				StringBuilder accumulator = new StringBuilder();
				int maxLength = 256;
				int prefixLength = prefix.length();
				int chunkLength = maxLength - prefixLength;

				GetResponse.getResponse(text, senderName, chatHistories, delta -> {
					fullResponse.append(delta);
					String sanitizedDelta = GetResponse.sanitizeMinecraftChat(delta);
					accumulator.append(sanitizedDelta);
					while (accumulator.length() >= chunkLength) {
						String chunk = accumulator.substring(0, chunkLength);
						accumulator.delete(0, chunkLength);
						String message = prefix + chunk;
						if (prefix.startsWith("/")) {
							String command = message.substring(1);
							client.execute(() -> client.getNetworkHandler().sendChatCommand(command));
						} else {
							client.execute(() -> client.getNetworkHandler().sendChatMessage(message));
						}
						try {
							int randomDelay = ThreadLocalRandom.current().nextInt(
								Math.max(1, (Integer) cfg.get("sendDelay") - (Integer) cfg.get("delayRandomFactor")),
								(Integer) cfg.get("sendDelay") + (Integer) cfg.get("delayRandomFactor")
							);
							Thread.sleep(randomDelay);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}
				});
				if (accumulator.length() > 0) {
					String remaining = accumulator.toString();
					String message = prefix + remaining;
					if (prefix.startsWith("/")) {
						String command = message.substring(1);
						client.execute(() -> client.getNetworkHandler().sendChatCommand(command));
					} else {
						client.execute(() -> client.getNetworkHandler().sendChatMessage(message));
					}
				}

				JsonObject assistantEntry = new JsonObject();
				assistantEntry.addProperty("role", "assistant");
				assistantEntry.addProperty("content", fullResponse.toString());
				history.addLast(assistantEntry);

				while (history.size() > 10) {
					history.removeFirst();
				}
			} catch (Exception e) {
				Notification.showNotification("error", "Failed to process response: " + e.getMessage());
			}
		}, "ChatRequest-Thread").start();
	}

	private String extractSenderName(String message) {
		if (message == null) return null;
		int open = message.indexOf('<');
		int close = message.indexOf('>', open);
		if (open == -1 || close == -1 || close <= open + 1) {
			return null;
		}
		String inside = message.substring(open + 1, close);
		return inside.replaceAll("§.", "");
	}
}
