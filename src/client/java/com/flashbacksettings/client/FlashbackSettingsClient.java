package com.flashbacksettings.client;

import com.flashbacksettings.FlashbackSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

public class FlashbackSettingsClient implements ClientModInitializer {

    private static KeyMapping openSettingsKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(FlashbackSettings.MOD_ID, "settings")
        );

        openSettingsKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                "key.flashback-settings.open_settings",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                category
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettingsKey.consumeClick()) {
                client.setScreenAndShow(new FlashbackSettingsScreen(null));
            }
        });
    }
}
