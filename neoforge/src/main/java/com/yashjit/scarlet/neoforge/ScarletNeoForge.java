package com.yashjit.scarlet.neoforge;

import com.yashjit.scarlet.Scarlet;
import com.yashjit.scarlet.command.ScarletCommands;
import com.yashjit.scarlet.event.ScarletEvents;
import com.yashjit.scarlet.neoforge.platform.NeoForgePlayerDataService;
import com.yashjit.scarlet.neoforge.platform.NeoForgeRegistryService;
import com.yashjit.scarlet.network.ScarletNetwork;
import com.yashjit.scarlet.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(Scarlet.MOD_ID)
public final class ScarletNeoForge {

    public ScarletNeoForge(IEventBus modBus, ModContainer container) {
        Scarlet.init();
        ((NeoForgeRegistryService) Services.REGISTRY).attach(modBus);
        NeoForgePlayerDataService.ATTACHMENTS.register(modBus);

        modBus.addListener(ScarletNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ScarletNeoForge::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ScarletNeoForge::onEquipmentChange);
        NeoForge.EVENT_BUS.addListener(ScarletNeoForge::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ScarletEvents.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ScarletCommands.register(event.getDispatcher()));
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!ScarletEvents.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
            event.setCanceled(true);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        ScarletNetwork.registerPayloads(new NeoForgePayloadRegistry(event.registrar(ScarletNetwork.VERSION)));
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScarletEvents.onPlayerTick(player);
        }
    }

    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        ScarletEvents.onEquipmentChange(event.getEntity(), event.getSlot(), event.getFrom(), event.getTo());
    }
}
