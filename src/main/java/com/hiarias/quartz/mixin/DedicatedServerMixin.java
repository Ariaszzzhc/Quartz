package com.hiarias.quartz.mixin;

import com.hiarias.quartz.bridge.DedicatedServerBridge;
import com.hiarias.quartz.event.PluginStartupCallback;
import net.minecraft.server.dedicated.DedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedServer.class)
public class DedicatedServerMixin implements DedicatedServerBridge {

    @Override
    @Inject(method = "initServer", at = @At(value = "INVOKE", target="Lnet/minecraft/server/dedicated/DedicatedServer;setPlayerList(Lnet/minecraft/server/players/PlayerList;)V", shift = At.Shift.AFTER))
    public void emitPluginStartupEvent(CallbackInfoReturnable<Boolean> info) {
        PluginStartupCallback.Companion.getEVENT().invoker().invoke();
    }
}
