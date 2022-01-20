package com.hiarias.quartz.scheduler

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.math.max

abstract class Scheduler {
    private val ids = AtomicInteger(1)

    protected fun nextId() = ids.incrementAndGet()

    @Volatile
    protected var head: QuartzTask = EmptyTask()

    protected val tail: AtomicReference<QuartzTask> = AtomicReference(head)

    protected val temp = ArrayList<QuartzTask>()

    val runners = ConcurrentHashMap<Int, QuartzTask>()

    @Volatile
    protected var currentTask: QuartzTask? = null

    @Volatile
    var currentTick: Int = -1

    val pending = PriorityQueue<QuartzTask>(10) { t1, t2 ->
        val v = t1.nextRun.compareTo(t2.nextRun)
        if (v != 0) {
            v
        } else {
            t1.taskId.compareTo(t2.taskId)
        }
    }

    protected fun parsePending() {
        var head = this.head
        var task = head.next
        var lastTask = head

        while (task != null) {
            if (task.taskId == -1) {
                task.run()
            } else if (task.period >= NO_REPEATING) {
                pending.add(task)
                runners[task.taskId] = task
            }
            lastTask = task
            task = task.next
        }


        task = head
        while(task != lastTask) {
            head = task!!.next!!
            task.next = null
            task = head
        }

        this.head = lastTask
    }

    abstract fun mainThreadHeartbeat(currentTick: Int)
}

class QuartzScheduler : BukkitScheduler, Scheduler() {

    private val asyncScheduler = AsyncScheduler()

    override fun runTask(plugin: Plugin, task: Runnable): BukkitTask =
        runTaskLater(plugin, task, 0L)

    @Throws(IllegalArgumentException::class)
    override fun runTask(plugin: Plugin, task: Consumer<BukkitTask>) =
        runTaskLater(plugin, task, 0L)

    override fun scheduleSyncDelayedTask(plugin: Plugin, task: Runnable) =
        scheduleSyncDelayedTask(plugin, task, 0L)

    override fun scheduleSyncDelayedTask(plugin: Plugin, task: Runnable, delay: Long) =
        scheduleSyncRepeatingTask(plugin, task, delay, NO_REPEATING)

    override fun scheduleSyncRepeatingTask(plugin: Plugin, task: Runnable, delay: Long, period: Long) =
        runTaskTimer(plugin, task, delay, period).taskId

    override fun runTaskLater(plugin: Plugin, task: Runnable, delay: Long) =
        runTaskTimer(plugin, task, delay, NO_REPEATING)

    @Throws(IllegalArgumentException::class)
    override fun runTaskLater(plugin: Plugin, task: Consumer<BukkitTask>, delay: Long) =
        runTaskTimer(plugin, task, delay, NO_REPEATING)

    override fun runTaskTimer(plugin: Plugin, task: Runnable, delay: Long, period: Long): BukkitTask {
        val d = max(0L, delay)

        return handle(createSyncTask(plugin, nextId(), period, task), d)
    }

    @Throws(IllegalArgumentException::class)
    override fun runTaskTimer(plugin: Plugin, task: Consumer<BukkitTask>, delay: Long, period: Long) {
        val d = max(0L, delay)

        handle(createSyncTask(plugin, nextId(), period, task), d)
    }

    override fun getPendingTasks(): MutableList<BukkitTask> {
        val truePending = mutableListOf<QuartzTask>()

        var task = head.next
        while (task != null) {
            if (task.taskId != -1) truePending.add(task)
            task = task.next
        }

        val ret = mutableListOf<BukkitTask>()

        runners.values.filter {
            it.period >= NO_REPEATING
        }.forEach {
            ret.add(it)
        }

        truePending.filter {
            it.period >= NO_REPEATING && !ret.contains(it)
        }.forEach {
            ret.add(it)
        }

        return ret
    }

    private fun handle(task: QuartzTask, delay: Long): BukkitTask {

        return task
    }

    override fun mainThreadHeartbeat(currentTick: Int) {
        this.asyncScheduler.mainThreadHeartbeat(currentTick)

        // TODO
    }

    private class AsyncScheduler : Scheduler() {
        private val executor = ThreadPoolExecutor(
            4,
            Int.MAX_VALUE,
            30L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            ThreadFactoryBuilder().setNameFormat("Quartz Scheduler Thread - %1\$d").build()
        )

        private val management =
            Executors.newSingleThreadExecutor(ThreadFactoryBuilder()
                .setNameFormat("Quartz Async Scheduler Management Thread").build())

        init {
            executor.allowCoreThreadTimeOut(true)
            executor.prestartCoreThread()
        }


        @Synchronized
        private fun removeTask(id: Int) {
            parsePending()
            pending.removeIf {
                if (it.taskId == id) {
                    it.cancel0()
                    return@removeIf true
                }
                false
            }
        }

        @Synchronized
        private fun runTasks(currentTick: Int) {
            parsePending()
            while (!pending.isEmpty() && pending.peek().nextRun <= currentTick) {
                val task = pending.remove()
                if (executeTask(task)) {
                    if (task.period > 0){
                        task.nextRun = (currentTick + task.period).toInt()
                        temp.add(task)
                    }
                }
                parsePending()
            }
            pending.addAll(temp)
            temp.clear()
        }

        private fun executeTask(task: QuartzTask): Boolean {
            if (isValid(task)) {
                runners[task.taskId] = task
                executor.execute(task)
                return true
            }

            return false
        }

        override fun mainThreadHeartbeat(currentTick: Int) {
            this.currentTick = currentTick
            management.execute {
                runTasks(currentTick)
            }
        }

        @Synchronized
        fun cancelTasks(plugin: Plugin) {
            parsePending()
            val it = pending.iterator()
            while (it.hasNext()) {
                val task = it.next()
                if (task.taskId != -1 && (task.owner == plugin)) {
                    task.cancel0()
                    it.remove()
                }
            }
        }

        companion object {
            fun isValid(task: QuartzTask) = task.period >= NO_REPEATING
        }
    }
}
