package com.hiarias.quartz

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.apache.logging.log4j.LogManager

object QuartzMod : ModInitializer {
    const val MOD_ID = "Quartz"

    @JvmStatic
    val logger = LogManager.getLogger(MOD_ID)

    override fun onInitialize() {
        logger.info("Hello, world")
        ServerLifecycleEvents.SERVER_STARTED.register {
            it.worlds.forEach { world ->
                logger.info(world.registryKey.toString())
            }
        }
    }
}
