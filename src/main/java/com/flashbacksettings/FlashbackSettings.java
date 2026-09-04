package com.flashbacksettings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class FlashbackSettings implements ModInitializer {

    public static final String MOD_ID = "flashback-settings";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static FlashbackSettings instance;
    private ModConfig config;

    @Override
    public void onInitialize() {
        instance = this;
        config = loadConfig();
        LOGGER.info("[FlashbackSettings] Loaded. Replay folder: {}", config.replayFolder.isEmpty() ? "(default)" : config.replayFolder);
    }

    public static FlashbackSettings getInstance() {
        return instance;
    }

    public ModConfig getConfig() {
        return config;
    }

    private ModConfig loadConfig() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve("flashbacksettings.json");

        if (!Files.exists(configFile)) {
            ModConfig defaults = new ModConfig();
            saveConfig(defaults, configFile);
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(configFile)) {
            ModConfig loaded = new Gson().fromJson(reader, ModConfig.class);
            if (loaded == null) {
                return new ModConfig();
            }
            if (loaded.replayFolder == null) {
                loaded.replayFolder = "";
            }
            return loaded;
        } catch (IOException e) {
            LOGGER.error("[FlashbackSettings] Failed to read config using default.", e);
            return new ModConfig();
        }
    }

    public void saveConfig() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("flashbacksettings.json");
        saveConfig(this.config, configFile);
    }

    private void saveConfig(ModConfig cfg, Path configFile) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(configFile)) {
            gson.toJson(cfg, writer);
        } catch (IOException e) {
            LOGGER.error("[FlashbackSettings] Failed to save config.", e);
        }
    }

    public static class ModConfig {
        public String replayFolder = "";
        public boolean dynamicSubfolders = false;
        public boolean subfolderByDate = true;
        public boolean subfolderByServer = true;
        public boolean subfolderByVersion = true;
    }
}
