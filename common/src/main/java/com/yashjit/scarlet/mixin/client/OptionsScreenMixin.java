package com.yashjit.scarlet.mixin.client;

import com.yashjit.scarlet.client.config.SettingsButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mod's settings are reached from the corner of the game's own options, on every loader alike.
 */
@Mixin(OptionsScreen.class)
abstract class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void scarlet$settingsButton(CallbackInfo ci) {
        addRenderableWidget(new SettingsButton(width - SettingsButton.SIZE - 6, 6));
    }
}
