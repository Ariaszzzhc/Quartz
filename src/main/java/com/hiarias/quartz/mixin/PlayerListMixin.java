package com.hiarias.quartz.mixin;

import com.hiarias.quartz.bridge.PlayerListBridge;
import com.hiarias.quartz.event.PlayerManagerStartedCallback;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin implements PlayerListBridge {

    @Override
    @Inject(method = "<init>", at = @At("TAIL"))
    public void emitPlayerListStartedEvent(MinecraftServer server, RegistryAccess.RegistryHolder registryHolder, PlayerDataStorage playerDataStorage, int i, CallbackInfo info) {
        PlayerManagerStartedCallback.Companion.getEVENT().invoker().interact(
            (DedicatedServer) server,
            (PlayerList) (Object) this
        );
    }
}
