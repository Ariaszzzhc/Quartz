package com.hiarias.quartz.mixin;

import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Properties;

@Mixin(DedicatedServerProperties.class)
public interface DedicatedServerPropertiesAccessor {
    @Accessor("properties")
    Properties getInternalProperties();
}
