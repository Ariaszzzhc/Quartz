package com.hiarias.quartz.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

interface QuartzCommand : Command<ServerCommandSource> {
    fun getName(): String

    override fun run(context: CommandContext<ServerCommandSource>): Int
}
