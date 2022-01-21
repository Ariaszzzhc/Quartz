package com.hiarias.quartz

import com.hiarias.quartz.event.PlayerManagerStartedCallback
import com.hiarias.quartz.event.PluginStartupCallback
import net.fabricmc.api.ModInitializer
import net.minecraft.server.dedicated.DedicatedPlayerList
import org.bukkit.plugin.PluginLoadOrder

object QuartzMod : ModInitializer {
    const val MOD_ID = "Quartz"

    @JvmStatic
    val logger = QuartzLogger(MOD_ID)

    var bukkit: QuartzServer? = null

    override fun onInitialize() {
        PlayerManagerStartedCallback.EVENT.register { server, players ->
            logger.info("Starting Quartz Server...")
            bukkit = QuartzServer(server, players as DedicatedPlayerList)
        }

        PluginStartupCallback.EVENT.register {
            bukkit?.loadPlugins()
            bukkit?.enablePlugins(PluginLoadOrder.STARTUP)
        }
    }
}
