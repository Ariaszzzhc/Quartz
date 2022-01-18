package com.hiarias.quartz.event

import net.fabricmc.fabric.api.event.EventFactory

fun interface PluginStartupCallback {
    fun invoke()

    companion object {
        val EVENT = EventFactory.createArrayBacked(
            PluginStartupCallback::class.java
        ) { listeners ->
            PluginStartupCallback {
                for (listener in listeners) {
                    listener.invoke()
                }
            }
        }
    }
}
