package com.hiarias.quartz.scheduler

import org.bukkit.plugin.Plugin
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class QuartzFuture<T>(
    private val callable: Callable<T>,
    plugin: Plugin,
    id: Long,
) : QuartzTask(plugin, null, id, NO_REPEATING), Future<T> {

    private var value: T? = null
    private var exception: Exception? = null

    @Synchronized
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (period != NO_REPEATING) {
            return false
        }

        period = CANCEL
        return true
    }

    override fun isDone() = period != NO_REPEATING && period != PROCESS_FOR_FUTURE

    override fun get(): T {
        try {
            return get(0, TimeUnit.MICROSECONDS)
        } catch (e: TimeoutException) {
            throw Error(e)
        }
    }

    @Synchronized
    override fun get(timeout: Long, unit: TimeUnit): T {
        TODO("Not yet implemented")
    }

    override fun run() {
        synchronized(this) {
            if (period == CANCEL) {
                return
            }
            period = PROCESS_FOR_FUTURE
        }

        try {
            value = callable.call()
        } catch (e: Exception) {
            exception = e
        } finally {
            synchronized(this) {
                period = DONE_FOR_FUTURE
                // TODO: notifyAll
            }
        }
    }

    override fun cancel0(): Boolean {
        if (period != NO_REPEATING) {
            return false
        }
        period = CANCEL
        // TODO: notify all
        return true
    }
}
