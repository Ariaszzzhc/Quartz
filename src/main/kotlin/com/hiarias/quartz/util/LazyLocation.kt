package com.hiarias.quartz.util

import kotlinx.serialization.Serializable
import net.minecraft.util.Identifier
import net.minecraft.util.registry.RegistryKey

@Serializable
data class LazyLocation(
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
) {
    fun toLocation(): Location {
        return Location(
            RegistryKey.of(RegistryKey.ofRegistry(Identifier(REGISTRY)), Identifier(worldName.ifEmpty {
                OVER_WORLD
            })),
            x,
            y,
            z,
            yaw,
            pitch
        )
    }

    companion object {
        const val REGISTRY = "minecraft:dimension"
        const val OVER_WORLD = "minecraft:overworld"
    }
}
