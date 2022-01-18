package com.hiarias.quartz.scheduler

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitWorker
import java.util.LinkedList
import java.util.logging.Level

class QuartzAsyncTask(
    private val runners: MutableMap<Int, QuartzTask>,
    plugin: Plugin,
    task: Any,
    id: Long,
    delay: Long
) : QuartzTask(plugin, task, id, delay) {

    val workers = LinkedList<BukkitWorker>()

    override fun isSync() = false

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
                    override fun getTaskId() = this@QuartzAsyncTask.taskId

                    override fun getOwner() = this@QuartzAsyncTask.owner

                    override fun getThread() = thread
                })
            }

            var throwable: Throwable? = null
            try {
                super.run()
            } catch (t: Throwable) {
                throwable = t
                owner.logger.log(Level.WARNING, "Plugin ${owner.description.fullName} generated an exception while executing task $taskId", throwable)
            } finally {
                synchronized(workers) {
                    try {
                        val wi = workers.iterator()
                        var removed = false
                        while (wi.hasNext()) {
                            if (wi.next().thread == thread) {
                                workers.remove()
                                removed = true
                                break
                            }
                        }

                        if (!removed) {
                            throw IllegalArgumentException(
                                "Unable to remove worker ${thread.name} on task $taskId for ${owner.description.fullName}",
                                throwable
                            )
                        }
                    } finally {
                        if (period < 0 && workers.isEmpty()) {
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
