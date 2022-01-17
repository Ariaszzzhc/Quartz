package com.hiarias.quartz.event

import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.players.PlayerList

fun interface PlayerManagerStartedCallback {
    fun interact(server: DedicatedServer, players: PlayerList)

    companion object {
        val EVENT = EventFactory.createArrayBacked(
            PlayerManagerStartedCallback::class.java
        ) { listeners ->
            PlayerManagerStartedCallback { server, players ->
                for (listener in listeners) {
                    listener.interact(server, players)
                }
            }
        }
    }
}
