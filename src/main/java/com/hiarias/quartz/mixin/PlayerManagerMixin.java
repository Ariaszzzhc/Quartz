package com.hiarias.quartz.mixin;

import com.hiarias.quartz.bridge.PlayerManagerBridge;
import com.hiarias.quartz.event.PlayerManagerStartedCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.WorldSaveHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin implements PlayerManagerBridge {

    @Override
    @Inject(method = "<init>", at = @At("TAIL"))
    public void emitPlayerListStartedEvent(MinecraftServer server, DynamicRegistryManager.Impl registryManager, WorldSaveHandler saveHandler, int maxPlayers, CallbackInfo info) {
        PlayerManagerStartedCallback.Companion.getEVENT().invoker().interact(
            (MinecraftDedicatedServer) server,
            (PlayerManager) (Object) this
        );
    }
}
