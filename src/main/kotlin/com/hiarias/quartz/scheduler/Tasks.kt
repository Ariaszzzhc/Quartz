package com.hiarias.quartz.scheduler

import com.hiarias.quartz.QuartzMod
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.ChunkGenerator
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginDescriptionFile
import org.bukkit.plugin.PluginLoader
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scheduler.BukkitWorker
import java.io.File
import java.io.InputStream
import java.util.LinkedList
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

const val ERROR = 0L
const val NO_REPEATING = -1L
const val CANCEL = -2L
const val PROCESS_FOR_FUTURE = -3L
const val DONE_FOR_FUTURE = -4L

abstract class QuartzTask(
    private val plugin: Plugin,
    private val id: Int,
    @Volatile
    var period: Long,
    private val sync: Boolean
) : BukkitTask, Runnable {
    @Volatile
    var next: QuartzTask? = null
    val createdAt = System.nanoTime()
    var nextRun = 0

    override fun isSync() = sync

    override fun getOwner() = plugin

    override fun getTaskId() = id

    override fun cancel() {
        Bukkit.getScheduler().cancelTask(id)
    }

    override fun isCancelled() = period == CANCEL

    abstract fun cancel0(): Boolean
}

fun createSyncTask(plugin: Plugin, id: Int, period: Long, task: Runnable): QuartzTask =
    SyncQuartzTask(plugin, { task.run() }, id, resolvePeriod(period))


fun createSyncTask(plugin: Plugin, id: Int, period: Long, task: Consumer<BukkitTask>): QuartzTask =
    SyncQuartzTask(plugin, { task.accept(it) }, id, resolvePeriod(period))

fun createAsyncTask(runners: MutableMap<Int, QuartzTask>, plugin: Plugin, id: Int, period: Long, task: Runnable): QuartzTask =
    AsyncQuartzTask(runners, plugin, { task.run() }, id, resolvePeriod(period))

fun createAsyncTask(runners: MutableMap<Int, QuartzTask>, plugin: Plugin, id: Int, period: Long, task: Consumer<BukkitTask>): QuartzTask =
    AsyncQuartzTask(runners, plugin, { task.accept(it) }, id, resolvePeriod(period))

private fun resolvePeriod(period: Long): Long {
    return if (period == ERROR) {
        1L
    } else if (period < NO_REPEATING) {
        NO_REPEATING
    } else {
        period
    }
}

class SyncQuartzTask(
    plugin: Plugin,
    private val task: (QuartzTask) -> Unit,
    id: Int,
    period: Long,
) : QuartzTask(plugin, id, period, true) {

    override fun run() {
        task.invoke(this)
    }

    override fun cancel0(): Boolean {
        period = CANCEL

        return true
    }
}

class AsyncQuartzTask(
    private val runners: MutableMap<Int, QuartzTask>,
    plugin: Plugin,
    private val task: (QuartzTask) -> Unit,
    id: Int,
    period: Long,
) : QuartzTask(plugin, id, period, false) {
    val workers = LinkedList<BukkitWorker>()

    override fun run() {
        val thread = Thread.currentThread()
        val nameBefore = thread.name

        thread.name = "$nameBefore - ${owner.name}"

        try {
            synchronized(workers) {
                if (period == CANCEL) {
                    return
                }

                workers.add(object : BukkitWorker {
                    override fun getTaskId() = this@AsyncQuartzTask.taskId

                    override fun getOwner() = this@AsyncQuartzTask.owner

                    override fun getThread() = thread
                })
            }
            var thrown: Throwable? = null

            try {
                task.invoke(this)
            } catch (t: Throwable) {
                thrown = t

                owner.logger.log(
                    Level.WARNING,
                    "Plugin ${owner.description.fullName} generated an exception while executing task $taskId",
                    thrown
                )
            } finally {
                synchronized(workers) {
                    try {
                        val its = workers.iterator()
                        var removed = false
                        while (its.hasNext()) {
                            if (its.next().thread == thread) {
                                removed = true
                                its.remove()
                                break
                            }
                        }

                        if (!removed) {
                            throw IllegalStateException(
                                "Unable to remove worker ${thread.name} on task $taskId for ${owner.description.fullName}",
                                thrown
                            ) // We don't want to lose the original exception, if any
                        }
                    } finally {
                        if (period < 0 && workers.isEmpty()) {
                            // At this spot, we know we are the final async task being executed!
                            // Because we have the lock, nothing else is running or will run because delay < 0
                            runners.remove(taskId)
                        }
                    }
                }
            }
        } finally {
            thread.name = nameBefore
        }
    }

    override fun cancel0(): Boolean {
        synchronized(workers) {
            period = CANCEL
            if (workers.isEmpty()) {
                runners.remove(taskId)
            }
        }

        return true
    }
}

class QuartzFuture<T>(
    plugin: Plugin,
    private val task: Callable<T>,
    id: Int,
): QuartzTask(plugin, id, NO_REPEATING, true), Future<T> {
    private val lock = Object()
    private var value: T? = null
    private var exception: Exception? = null

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (period != NO_REPEATING) {
            return false
        }
        period = CANCEL

        return true
    }

    override fun run() {
        synchronized(lock) {
            if (period == CANCEL) {
                return
            }
            period = PROCESS_FOR_FUTURE
        }

        try {
            value = task.call()
        } catch (e: Exception) {
            exception = e
        } finally {
            synchronized(lock) {
                period = DONE_FOR_FUTURE
                lock.notifyAll()
            }
        }
    }

    override fun isDone() = period != NO_REPEATING && period != PROCESS_FOR_FUTURE

    override fun get(): T {
        try {
            return get(0, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            throw Error(e)
        }
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
        synchronized(lock) {
            var to = unit.toMillis(timeout)
            var timestamp = if (to > 0) {
                System.currentTimeMillis()
            } else {
                0L
            }

            while (true) {
                if (period == NO_REPEATING || period == PROCESS_FOR_FUTURE) {
                    lock.wait(to)

                    if (period == NO_REPEATING || period == PROCESS_FOR_FUTURE) {
                        if (to == 0L) {
                            continue
                        }
                        val currentTimeStamp = System.currentTimeMillis()
                        to += timestamp - currentTimeStamp
                        timestamp = currentTimeStamp
                        if (to > 0L) {
                            continue
                        }

                        throw TimeoutException()
                    }
                }

                if (period == CANCEL) {
                    throw CancellationException()
                }

                if (period == DONE_FOR_FUTURE) {
                    if (exception == null) {
                        return value!!
                    }
                    throw ExecutionException(exception)
                }

                throw IllegalStateException("Expected $NO_REPEATING to $DONE_FOR_FUTURE, got $period")
            }
        }
    }

    override fun cancel0(): Boolean {
        synchronized(lock) {
            if (period != NO_REPEATING) {
                return false
            }
            period = CANCEL
            lock.notifyAll()
            return true
        }
    }
}

open class EmptyTask : QuartzTask(
    EmptyPlugin(),
    NO_REPEATING.toInt(),
    NO_REPEATING,
    true
) {
    override fun run() {}

    override fun cancel0(): Boolean {
        period = CANCEL
        return true
    }

    private class EmptyPlugin : Plugin {
        var enabled = true

        private val pdf = PluginDescriptionFile("QuartzEmpty", "1.0", "quartz")

        override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            alias: String,
            args: Array<out String>
        ): MutableList<String>? {
            TODO("Not yet implemented")
        }

        override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>
        ): Boolean {
            TODO("Not yet implemented")
        }

        override fun getDataFolder(): File {
            TODO("Not yet implemented")
        }

        override fun getDescription(): PluginDescriptionFile = pdf

        override fun getConfig(): FileConfiguration {
            TODO("Not yet implemented")
        }

        override fun getResource(filename: String): InputStream? {
            TODO("Not yet implemented")
        }

        override fun saveConfig() {
            TODO("Not yet implemented")
        }

        override fun saveDefaultConfig() {
            TODO("Not yet implemented")
        }

        override fun saveResource(resourcePath: String, replace: Boolean) {
            TODO("Not yet implemented")
        }

        override fun reloadConfig() {
            TODO("Not yet implemented")
        }

        override fun getPluginLoader(): PluginLoader {
            TODO("Not yet implemented")
        }

        override fun getServer(): Server {
            TODO("Not yet implemented")
        }

        override fun isEnabled() = enabled

        override fun onDisable() {
            TODO("Not yet implemented")
        }

        override fun onLoad() {
            TODO("Not yet implemented")
        }

        override fun onEnable() {
            TODO("Not yet implemented")
        }

        override fun isNaggable(): Boolean {
            TODO("Not yet implemented")
        }

        override fun setNaggable(canNag: Boolean) {
            TODO("Not yet implemented")
        }

        override fun getDefaultWorldGenerator(worldName: String, id: String?): ChunkGenerator? {
            TODO("Not yet implemented")
        }

        override fun getDefaultBiomeProvider(worldName: String, id: String?): BiomeProvider? {
            TODO("Not yet implemented")
        }

        override fun getLogger(): Logger = QuartzMod.logger

        override fun getName(): String {
            TODO("Not yet implemented")
        }
    }
}
