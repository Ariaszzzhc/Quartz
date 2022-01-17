package com.hiarias.quartz.event

import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.PlayerManager
import net.minecraft.server.dedicated.MinecraftDedicatedServer
import net.minecraft.util.ActionResult

@FunctionalInterface
fun interface PlayerManagerStartedCallback {
    fun interact(server: MinecraftDedicatedServer, players: PlayerManager): ActionResult

    companion object {
        val EVENT = EventFactory.createArrayBacked(
            PlayerManagerStartedCallback::class.java
        ) { listeners ->
            PlayerManagerStartedCallback { server, players ->
                for (listener in listeners) {
                    val result = listener.interact(server, players)
                    if (result != ActionResult.PASS) {
                        return@PlayerManagerStartedCallback result
                    }
                }

                ActionResult.PASS
            }
        }
    }
}
