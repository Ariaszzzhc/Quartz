package com.hiarias.quartz.scheduler

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scheduler.BukkitWorker
import java.util.PriorityQueue
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.logging.Level
import kotlin.math.max

abstract class Scheduler {
    private val ids = AtomicInteger(1)

    protected fun nextId() = ids.incrementAndGet()

    @Volatile
    protected var head: QuartzTask = EmptyTask()

    private val tail: AtomicReference<QuartzTask> = AtomicReference(head)

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
        while (task != lastTask) {
            head = task!!.next!!
            task.next = null
            task = head
        }

        this.head = lastTask
    }

    fun pendingTasks(): MutableList<BukkitTask> {
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

    fun queued(taskId: Int): Boolean {
        var task = head.next
        while (task != null) {
            if (task.taskId == taskId) {
                return task.period >= NO_REPEATING
            }
            task = task.next
        }

        val t = runners[taskId]
        return t != null && t.period >= NO_REPEATING
    }

    open fun handle(task: QuartzTask, delay: Long): BukkitTask {
        task.nextRun = (currentTick + delay).toInt()

        addTask(task)
        return task
    }

    private fun addTask(task: QuartzTask) {
        var tailTask = tail.get()

        while (!tail.compareAndSet(tailTask, task)) {
            tailTask = tail.get()
        }

        tailTask.next = task
    }

    abstract fun mainThreadHeartbeat(currentTick: Int)

    abstract fun isCurrentlyRunning(taskId: Int): Boolean

    abstract fun getActiveWorkers(): MutableList<BukkitWorker>

    abstract fun cancelTask(taskId: Int)

    abstract fun cancelTasks(plugin: Plugin)
}

@Suppress("DEPRECATION")
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

    override fun <T> callSyncMethod(plugin: Plugin, task: Callable<T>): Future<T> {
        val future = QuartzFuture(plugin, task, nextId())
        handle(future, 0L)
        return future
    }

    @Deprecated("Do not use this!", ReplaceWith("runTaskAsynchronously(plugin, task)"))
    override fun scheduleAsyncDelayedTask(plugin: Plugin, task: Runnable) =
        scheduleAsyncDelayedTask(plugin, task, 0L)

    override fun scheduleAsyncRepeatingTask(plugin: Plugin, task: Runnable, delay: Long, period: Long) =
        runTaskTimerAsynchronously(plugin, task, delay, period).taskId

    override fun runTaskAsynchronously(plugin: Plugin, task: Runnable) =
        runTaskLaterAsynchronously(plugin, task, 0L)

    @Throws(IllegalArgumentException::class)
    override fun runTaskAsynchronously(plugin: Plugin, task: Consumer<BukkitTask>) =
        runTaskLaterAsynchronously(plugin, task, 0L)

    @Deprecated("Do not use this!", ReplaceWith("runTaskLaterAsynchronously(plugin, task, delay)"))
    override fun scheduleAsyncDelayedTask(plugin: Plugin, task: Runnable, delay: Long) =
        scheduleAsyncRepeatingTask(plugin, task, delay, NO_REPEATING)

    override fun runTaskLaterAsynchronously(plugin: Plugin, task: Runnable, delay: Long) =
        runTaskTimerAsynchronously(plugin, task, delay, NO_REPEATING)

    @Throws(IllegalArgumentException::class)
    override fun runTaskLaterAsynchronously(plugin: Plugin, task: Consumer<BukkitTask>, delay: Long) =
        runTaskTimerAsynchronously(plugin, task, delay, NO_REPEATING)

    @Throws(IllegalArgumentException::class)
    override fun runTaskTimerAsynchronously(plugin: Plugin, task: Consumer<BukkitTask>, delay: Long, period: Long) {
        val d = max(0L, delay)

        handle(createAsyncTask(asyncScheduler.runners, plugin, nextId(), period, task), d)
    }

    override fun runTaskTimerAsynchronously(plugin: Plugin, task: Runnable, delay: Long, period: Long): BukkitTask {
        val d = max(0L, delay)

        return handle(createAsyncTask(asyncScheduler.runners, plugin, nextId(), period, task), d)
    }

    override fun isCurrentlyRunning(taskId: Int): Boolean {
        if (asyncScheduler.isCurrentlyRunning(taskId)) return true

        val task = runners[taskId] ?: return false

        return task == currentTask
    }

    override fun isQueued(taskId: Int): Boolean {
        if (this.asyncScheduler.queued(taskId)) {
            return true
        }

        return queued(taskId)
    }

    override fun getActiveWorkers(): MutableList<BukkitWorker> {
        return asyncScheduler.getActiveWorkers()
    }

    override fun cancelTask(taskId: Int) {
        if (taskId < 0) return

        asyncScheduler.cancelTask(taskId)

        runners[taskId]?.cancel0()

        val task = object : EmptyTask() {
            override fun run() {
                if (!check(this@QuartzScheduler.temp)) {
                    check(this@QuartzScheduler.pending)
                }
            }

            private fun check(collection: MutableIterable<QuartzTask>): Boolean {
                val tasks = collection.iterator()
                while (tasks.hasNext()) {
                    val task = tasks.next()
                    if (task.taskId == taskId) {
                        task.cancel0()
                        tasks.remove()
                        if (task.isSync) {
                            runners.remove(taskId)
                        }

                        return true
                    }
                }

                return false
            }
        }

        handle(task, 0L)

        var taskPending = head.next
        while (taskPending != null) {
            if (taskPending == task) return
            if (taskPending.taskId == taskId) taskPending.cancel0()

            taskPending = taskPending.next
        }
    }

    override fun cancelTasks(plugin: Plugin) {
        asyncScheduler.cancelTasks(plugin)

        val task = object : EmptyTask() {
            override fun run() {
                check(this@QuartzScheduler.temp)
                check(this@QuartzScheduler.pending)
            }

            fun check(collection: MutableIterable<QuartzTask>) {
                val tasks = collection.iterator()
                while (tasks.hasNext()) {
                    val task = tasks.next()
                    if (task.owner == plugin) {
                        task.cancel0()
                        tasks.remove()

                        if (task.isSync) {
                            runners.remove(task.taskId)
                        }
                    }
                }
            }
        }

        handle(task, 0L)

        var taskPending = head.next
        while (taskPending != null) {
            if (taskPending == task) break
            if (taskPending.taskId != -1 && taskPending.owner == plugin) taskPending.cancel0()

            taskPending = taskPending.next
        }

        runners.values.forEach {
            if (it.owner == plugin) {
                it.cancel0()
            }
        }
    }

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

    override fun getMainThreadExecutor(plugin: Plugin): Executor {
        return Executor {
            runTask(plugin, it)
        }
    }

    override fun getPendingTasks(): MutableList<BukkitTask> {
        val ret = pendingTasks()
        ret.addAll(asyncScheduler.pendingTasks())

        return ret
    }

    override fun handle(task: QuartzTask, delay: Long): BukkitTask {
        return if (task.isSync) {
            super.handle(task, delay)
        } else {
            asyncScheduler.handle(task, delay)
        }
    }

    override fun mainThreadHeartbeat(currentTick: Int) {
        this.asyncScheduler.mainThreadHeartbeat(currentTick)

        this.currentTick = currentTick
        parsePending()

        while(isReady(currentTick)) {
            val task = pending.remove()
            if (task.period < NO_REPEATING) {
                if (task.isSync) {
                    runners.remove(task.taskId, task)
                }
                parsePending()
                continue
            }

            if (task.isSync) {
                currentTask = task
                try {
                    task.run()
                } catch (t: Throwable) {
                    task.owner.logger.log(
                        Level.WARNING,
                        "Task #${task.taskId} for ${task.owner.description.fullName} generated an exception",
                        t
                    )
                } finally {
                    currentTask = null
                }

                parsePending()
            } else {
                task.owner.logger.log(
                    Level.SEVERE,
                    "Unexpected Async Task in the Sync Scheduler. Report this to Quartz"
                )
            }

            if (task.period > 0) {
                task.nextRun = (currentTick + task.period).toInt()
                temp.add(task)
            } else if (task.isSync) {
                runners.remove(task.taskId)
            }
        }

        pending.addAll(temp)
        temp.clear()
    }

    private fun isReady(tick: Int): Boolean {
        return !pending.isEmpty() && pending.peek().nextRun <= tick
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
            Executors.newSingleThreadExecutor(
                ThreadFactoryBuilder()
                    .setNameFormat("Quartz Async Scheduler Management Thread").build()
            )

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
                    true
                } else {
                    false
                }
            }
        }

        @Synchronized
        private fun runTasks(currentTick: Int) {
            parsePending()
            while (!pending.isEmpty() && pending.peek().nextRun <= currentTick) {
                val task = pending.remove()
                if (executeTask(task)) {
                    if (task.period > 0) {
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

        override fun isCurrentlyRunning(taskId: Int): Boolean {
            val task = runners[taskId] ?: return false

            val aTask = task as AsyncQuartzTask

            return synchronized(aTask.workers) {
                !aTask.workers.isEmpty()
            }
        }

        override fun getActiveWorkers(): MutableList<BukkitWorker> {
            val ret = ArrayList<BukkitWorker>()
            runners.values.filter {
                !it.isSync
            }.forEach {
                val task = it as AsyncQuartzTask
                synchronized(task.workers) {
                    ret.addAll(task.workers)
                }
            }

            return ret
        }

        override fun cancelTask(taskId: Int) {
            management.execute {
                removeTask(taskId)
            }
        }

        @Synchronized
        override fun cancelTasks(plugin: Plugin) {
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

    override fun toString(): String {
        return ""
    }

    override fun scheduleSyncDelayedTask(plugin: Plugin, task: BukkitRunnable): Int {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskLater(Plugin, long)")
    }

    override fun runTask(plugin: Plugin, task: BukkitRunnable): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTask(Plugin)")
    }

    override fun runTaskAsynchronously(plugin: Plugin, task: BukkitRunnable): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskAsynchronously(Plugin)")
    }

    override fun runTaskLater(plugin: Plugin, task: BukkitRunnable, delay: Long): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskLater(Plugin, long)")
    }

    override fun runTaskTimer(plugin: Plugin, task: BukkitRunnable, delay: Long, period: Long): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskTimer(Plugin, long, long)")
    }

    override fun runTaskTimerAsynchronously(
        plugin: Plugin,
        task: BukkitRunnable,
        delay: Long,
        period: Long
    ): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskTimerAsynchronously(Plugin, long, long)")
    }

    override fun scheduleSyncDelayedTask(plugin: Plugin, task: BukkitRunnable, delay: Long): Int {
        throw UnsupportedOperationException("Use BukkitRunnable#runTask(Plugin)")
    }

    override fun scheduleSyncRepeatingTask(plugin: Plugin, task: BukkitRunnable, delay: Long, period: Long): Int {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskTimer(Plugin, long, long)")
    }

    override fun runTaskLaterAsynchronously(plugin: Plugin, task: BukkitRunnable, delay: Long): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskLaterAsynchronously(Plugin, long)")
    }
}
