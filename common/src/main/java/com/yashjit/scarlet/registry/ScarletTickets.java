package com.yashjit.scarlet.registry;

import com.yashjit.scarlet.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

public final class ScarletTickets {

    /**
     * Keeps a dreamwalker's body and the creature their spirit is in loaded and moving, the way an ender pearl in flight
     * keeps the ground ahead of it, even in a dimension no one else is in. Lapses two seconds after it was last renewed.
     */
    public static final Supplier<TicketType> DREAMWALK = Services.REGISTRY.register(BuiltInRegistries.TICKET_TYPE, "dreamwalk",
            () -> new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    private ScarletTickets() {
    }

    public static void bootstrap() {
    }
}
