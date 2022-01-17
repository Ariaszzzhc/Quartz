package com.hiarias.quartz

import com.hiarias.quartz.event.PlayerManagerStartedCallback
import net.fabricmc.api.ModInitializer
import net.minecraft.server.dedicated.DedicatedPlayerManager
import net.minecraft.util.ActionResult
import org.apache.logging.log4j.LogManager

object QuartzMod : ModInitializer {
    const val MOD_ID = "Quartz"

    @JvmStatic
    val logger = LogManager.getLogger(MOD_ID)

    var bukkit: QuartzServer? = null

    override fun onInitialize() {
        PlayerManagerStartedCallback.EVENT.register { server, players ->
            logger.info("Starting Quartz Server...")
            bukkit = QuartzServer(server, players as DedicatedPlayerManager)
            ActionResult.PASS
        }
    }
}
