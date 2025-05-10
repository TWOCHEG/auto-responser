package com.responser.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class Notification {
    public static void showNotification(String title, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getToastManager() != null) {
            // Показ уведомления
            client.getToastManager().add(
                new SystemToast(
                    SystemToast.Type.NARRATOR_TOGGLE,
                    Text.literal(title),
                    Text.literal(message)
                )
            );

            // Проигрывание звука
            if (client.player != null) {
                client.player.playSound(
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP
                );
            }
        }
    }
}