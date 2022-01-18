package com.hiarias.quartz.scheduler

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.function.Consumer

open class QuartzTask(
    private val plugin: Plugin?,
    task: Any?,
    private val id: Long,
    @Volatile
    var period: Long
) : BukkitTask, Runnable {

    private val runnable: Runnable?
    private val consumer: Consumer<BukkitTask>?
    val createdAt = System.nanoTime()
    var nextRun: Long = 0L

    @Volatile
    var next: QuartzTask? = null

    // TODO: implement paper timing

    constructor() : this(null, null, NO_REPEATING, NO_REPEATING)

    constructor(task: Any?) : this (null, task, NO_REPEATING, NO_REPEATING)

    init {
        when (task) {
            is Runnable -> {
                runnable = task
                consumer = null
            }
            is Consumer<*> -> {
                consumer = task as Consumer<BukkitTask>
                runnable = null
            }
            null -> {
                consumer = null
                runnable = null
            }
            else -> {
                throw AssertionError("Illegal task class $task")
            }
        }
    }

    override fun getTaskId(): Int = id.toInt()

    // TODO: Plugin null check
    override fun getOwner(): Plugin = plugin!!

    override fun isSync() = true

    override fun isCancelled() = period == CANCEL

    override fun cancel() {
        Bukkit.getScheduler().cancelTask(id.toInt())
    }

    override fun run() {
        if (runnable != null) {
            runnable.run()
        } else {
            consumer?.accept(this)
        }
    }

    open fun cancel0(): Boolean {
        period = CANCEL
        return true
    }

    companion object {
        const val ERROR = 0L
        const val NO_REPEATING = -1L
        const val CANCEL = -2L
        const val PROCESS_FOR_FUTURE = -3L
        const val DONE_FOR_FUTURE = -4L
    }
}
