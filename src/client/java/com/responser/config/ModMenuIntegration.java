package com.responser.config;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.text.Text;

import com.responser.config.ConfigManager;

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
                            .setDefaultValue((Boolean) cfg.get("enable"))
                            .setSaveConsumer(newValue -> {
                                cfg.set("enable", newValue);
                            })
                            .build())
                    .addEntry(builder.entryBuilder()
                            .startStrField(Text.translatable("API KEY"), cfg.get("apiKey"))
                            .setDefaultValue((String) cfg.get("apiKey"))
                            .setSaveConsumer(newValue -> {
                                cfg.set("apiKey", newValue);
                            })
                            .build())
                    .addEntry(builder.entryBuilder()
                            .startStrField(Text.translatable("MODEL"), cfg.get("modelId"))
                            .setDefaultValue((String) cfg.get("modelId"))
                            .setSaveConsumer(newValue -> {
                                cfg.set("modelId", newValue);
                            })
                            .build())
                    .addEntry(builder.entryBuilder()
                            .startIntField(Text.translatable("WAITTNG TIME (seconds)"), cfg.get("delayS"))
                            .setDefaultValue((int) cfg.get("delayS"))
                            .setMin(0)
                            .setMax(60)
                            .setSaveConsumer(newValue -> {
                                cfg.set("delayS", newValue);
                            })
                            .build())
                    .addEntry(builder.entryBuilder()
                        .startStrField(Text.translatable("CUSTOM MENTIONS (split for \",\")"), cfg.get("customMentions"))
                        .setDefaultValue((String) cfg.get("customMentions"))
                        .setSaveConsumer(newValue -> {
                            cfg.set("customMentions", newValue);
                        })
                        .build());

            return builder.build();
        };
    }
}
