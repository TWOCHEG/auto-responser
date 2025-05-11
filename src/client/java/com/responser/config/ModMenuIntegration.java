package com.responser.config;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        ConfigManager cfg = ConfigManager.getInstance();
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("Auto Responser"));

            builder.getOrCreateCategory(Text.translatable("MAIN"))
                .addEntry(builder.entryBuilder()
                    .startBooleanToggle(Text.translatable("ENABLE"), cfg.get("enable"))
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> {
                        cfg.set("enable", newValue);
                    })
                    .build())
                .addEntry(builder.entryBuilder()
                    .startStrField(Text.translatable("API KEY"), cfg.get("apiKey"))
                    .setDefaultValue("sk-...")
                    .setSaveConsumer(newValue -> {
                        cfg.set("apiKey", newValue);
                    })
                    .build())
                .addEntry(builder.entryBuilder()
                    .startStrField(Text.translatable("MODEL"), cfg.get("modelId"))
                    .setDefaultValue("meta-llama/llama-3.3-70b-instruct")
                    .setSaveConsumer(newValue -> {
                        cfg.set("modelId", newValue);
                    })
                    .build())
                .addEntry(builder.entryBuilder()
                    .startIntField(Text.translatable("WAITTNG TIME (seconds)"), cfg.get("delayS"))
                    .setDefaultValue(3)
                    .setMin(0)
                    .setMax(60)
                    .setSaveConsumer(newValue -> {
                        cfg.set("delayS", newValue);
                    })
                    .build())
                .addEntry(builder.entryBuilder()
                    .startStrField(Text.translatable("CUSTOM MENTIONS (split for \",\")"), cfg.get("customMentions"))
                    .setDefaultValue("")
                    .setSaveConsumer(newValue -> {
                        cfg.set("customMentions", newValue);
                    })
                    .build())
                .addEntry(builder.entryBuilder()
                    .startBooleanToggle(Text.translatable("AUTO MENTIONS OUTPUT"), cfg.get("autoOutputMentions"))
                    .setTooltip(Text.translatable("your username will be decomposed into 4 options: just username, username translated into Russian letters, and these 2 options without numbers"))
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> {
                        cfg.set("autoOutputMentions", newValue);
                    })
                    .build());
                var delayCategory = builder.getOrCreateCategory(Text.translatable("MESSAGE SEND DELAY (for antispam system)"));
                delayCategory.addEntry(builder.entryBuilder()
                    .startIntField(Text.translatable("DELAY (ms)"), cfg.get("sendDelay")) // Текущее значение из конфига
                    .setDefaultValue(1000)
                    .setMin(0)
                    .setMax(10000)
                    .setSaveConsumer(
                        newValue -> cfg.set("sendDelay", newValue)
                    )
                    .build());
                delayCategory.addEntry(builder.entryBuilder()
                    .startIntField(Text.translatable("DELAY RANDOM FACTOR (ms)"), cfg.get("delayRandomFactor"))
                    .setDefaultValue(500)
                    .setMin(0)
                    .setMax(5000)
                    .setSaveConsumer(
                        newValue -> cfg.set("delayRandomFactor", newValue)
                    )
                    .build());

            return builder.build();
        };
    }
}
