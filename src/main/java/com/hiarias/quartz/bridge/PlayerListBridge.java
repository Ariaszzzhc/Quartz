package com.hiarias.quartz.bridge;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public interface PlayerListBridge {
    void emitPlayerListStartedEvent(MinecraftServer server, RegistryAccess.RegistryHolder registryHolder, PlayerDataStorage playerDataStorage, int i, CallbackInfo info);
}
