package com.hiarias.quartz.util

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.RegistryKey
import net.minecraft.world.World

data class Location(
    val world: RegistryKey<World>,
    val pos: Vec3d,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    constructor(world: RegistryKey<World>, x: Double, y: Double, z: Double, yaw: Float = 0f, pitch: Float = 0f) : this(
        world,
        Vec3d(x, y, z),
        yaw,
        pitch
    )

    constructor(player: ServerPlayerEntity) : this(
        player.world.registryKey,
        Vec3d.ZERO.add(player.pos),
        player.headYaw,
        player.pitch
    )
}
