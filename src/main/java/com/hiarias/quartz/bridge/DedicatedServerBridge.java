package com.hiarias.quartz.bridge;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public interface DedicatedServerBridge {
    void emitPluginStartupEvent(CallbackInfoReturnable<Boolean> info);
}
