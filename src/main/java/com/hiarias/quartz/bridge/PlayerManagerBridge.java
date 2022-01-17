package com.hiarias.quartz.bridge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.WorldSaveHandler;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public interface PlayerManagerBridge {
    void emitPlayerListStartedEvent(MinecraftServer server, DynamicRegistryManager.Impl registryManager, WorldSaveHandler saveHandler, int maxPlayers, CallbackInfo info);
}
