package com.hiarias.quartz.mixin;

import com.destroystokyo.paper.utils.PaperPluginLogger;
import com.hiarias.quartz.QuartzLogger;
import org.bukkit.plugin.PluginDescriptionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.logging.Logger;

@Mixin(value = PaperPluginLogger.class, remap = false)
public class PluginLoggerMixin {
    @Overwrite
    public static Logger getLogger(PluginDescriptionFile pdf) {
        return new QuartzLogger(pdf.getPrefix() != null ? pdf.getPrefix() : pdf.getName());
    }
}
