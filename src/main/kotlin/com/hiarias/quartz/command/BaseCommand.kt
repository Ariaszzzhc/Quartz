package com.hiarias.quartz.command

abstract class BaseCommand(
    private val name: String
) : QuartzCommand {

    override fun getName() = name

}
