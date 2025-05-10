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
import java.util.Map;
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
	// Map username → list of chat entries (each a JsonObject with "role" and "content")
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

				processMessage(message.getString(), sender.getName(), client, customKeyBinding);
			}
		);
		// для applecraft.online (там конченый плагин на чат, а я там играю)
		ClientReceiveMessageEvents.GAME.register(
			(Text message, boolean overlay) -> {
				MinecraftClient client = MinecraftClient.getInstance();
				if (client.player == null) return;

				String text = message.getString();
				String senderName = extractSenderName(text);

				if (senderName == null || senderName.equals(client.getSession().getUsername())) return;

				processMessage(message.getString(), senderName, client, customKeyBinding);
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
				client.execute(() -> response(senderName, text, prefix, client));
			}
			resetFlags();
		}, ((Number) cfg.get("delayS")).longValue(), TimeUnit.SECONDS);
	}

	private void resetFlags() {
		cancelTask = false;
	}

	private void processMessage(String text, String senderName, MinecraftClient client, KeyBinding customKeyBinding) {
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
			ConfigManager cfg = ConfigManager.getInstance();

			Notification.showNotification(
					"mentioned!",
					MessageFormat.format(
							"gen will start in {0}s, press {1} or open the chat to cancel", cfg.get("delayS"), I18n.translate(customKeyBinding.getBoundKeyTranslationKey()).toUpperCase()
					)
			);

			scheduleMessageProcessing(senderName, cleanedText, client, prefix, cfg);
		}
	}

	public static void response(String senderName, String text, String prefix, MinecraftClient client) {
		new Thread(() -> {
			String resp = GetResponse.getResponse(text, senderName, chatHistories);
			if (resp == null) return;

			int maxLength = 256;
			int prefixLength = prefix.length();
			int chunkLength = maxLength - prefixLength;

			List<String> chunks = new ArrayList<>();
			for (int i = 0; i < resp.length(); i += chunkLength) {
				int end = Math.min(i + chunkLength, resp.length());
				chunks.add(resp.substring(i, end));
			}

			for (String chunk : chunks) {
				if (prefix.startsWith("/")) {
					String command = prefix + chunk;
					client.execute(() -> client.getNetworkHandler().sendChatCommand(command.substring(1)));
				} else {
					String reply = prefix + chunk;
					client.execute(() -> client.getNetworkHandler().sendChatMessage(reply));
				}
				try {
					// обход систем анти спама (вдруг есть)
					int randomDelay = ThreadLocalRandom.current().nextInt(2000, 4000);
					System.out.println(randomDelay);
					Thread.sleep(randomDelay);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
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
