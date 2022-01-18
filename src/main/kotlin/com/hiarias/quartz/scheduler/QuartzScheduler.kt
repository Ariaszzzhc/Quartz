package com.hiarias.quartz.scheduler

import org.apache.commons.lang.Validate
import org.bukkit.plugin.IllegalPluginAccessException
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scheduler.BukkitWorker
import java.util.PriorityQueue
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.IntUnaryOperator
import kotlin.math.max

@Suppress("DEPRECATION")
open class QuartzScheduler(
    private val isAsync: Boolean
) : BukkitScheduler {
    private val ids = AtomicInteger(START_ID)

    constructor() : this(false)

    @Volatile
    private var head = QuartzTask()

    private val tail = AtomicReference(head)

    val pending = PriorityQueue<QuartzTask>(10) { t1, t2 ->
        val value = t1.nextRun.compareTo(t2.nextRun)

        if (value != 0) {
            value
        } else {
            t1.createdAt.compareTo(t2.createdAt)
        }
    }

    private val temp = ArrayList<QuartzTask>()

    val runners = ConcurrentHashMap<Int, QuartzTask>()

    @Volatile
    var currentTask: QuartzTask? = null

    @Volatile
    var currentTick: Int = -1

    private val asyncScheduler = if (isAsync) {
        this
    } else {
        QuartzAsyncScheduler()
    }



    override fun scheduleSyncDelayedTask(plugin: Plugin, task: Runnable, delay: Long) =
        scheduleSyncRepeatingTask(plugin, task, delay, QuartzTask.NO_REPEATING)

    @Deprecated("Unsupported")
    override fun scheduleSyncDelayedTask(plugin: Plugin, task: BukkitRunnable, delay: Long): Int {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskLater(Plugin, long)")
    }

    override fun scheduleSyncDelayedTask(plugin: Plugin, task: Runnable) =
        scheduleSyncDelayedTask(plugin, task, 0L)

    @Deprecated("Unsupported")
    override fun scheduleSyncDelayedTask(plugin: Plugin, task: BukkitRunnable): Int {
        throw UnsupportedOperationException("Use BukkitRunnable#runTask(Plugin)")
    }

    override fun scheduleSyncRepeatingTask(plugin: Plugin, task: Runnable, delay: Long, period: Long) =
        runTaskTimer(plugin, task, delay, period).taskId

    @Deprecated("Unsupported")
    override fun scheduleSyncRepeatingTask(plugin: Plugin, task: BukkitRunnable, delay: Long, period: Long): Int {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskTimer(Plugin, long, long)")
    }

    override fun scheduleAsyncDelayedTask(plugin: Plugin, task: Runnable, delay: Long) =
        scheduleAsyncRepeatingTask(plugin, task, delay, QuartzTask.NO_REPEATING)

    override fun scheduleAsyncDelayedTask(plugin: Plugin, task: Runnable) =
        scheduleAsyncDelayedTask(plugin, task, 0L)

    override fun scheduleAsyncRepeatingTask(plugin: Plugin, task: Runnable, delay: Long, period: Long) =
        runTaskTimerAsynchronously(plugin, task, delay, period).taskId

    override fun <T> callSyncMethod(plugin: Plugin, task: Callable<T>): Future<T> {
        validate(plugin, task)
        TODO("Not yet implemented")
    }

    override fun cancelTask(taskId: Int) {
        if (taskId <= 0) {
            return
        }

        if (!this.isAsync) {
            this.asyncScheduler.cancelTask(taskId)
        }

        var task = runners[taskId]

        task?.cancel0()

        task = QuartzTask(object : Runnable {
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
                            this@QuartzScheduler.runners.remove(taskId)
                        }
                        return true
                    }
                }

                return false
            }
        })

        handle(task, 0L)


        var taskPending = head.next
        while (taskPending != null) {
            if (taskPending === task) {
                return
            }
            if (taskPending.taskId == taskId) {
                taskPending.cancel0()
            }
            taskPending = taskPending.next
        }
    }

    override fun cancelTasks(plugin: Plugin) {
        Validate.notNull(plugin, "Cannot cancel tasks of null plugin")

        if (!isAsync) {
            asyncScheduler.cancelTasks(plugin)
        }

        val task = QuartzTask(object : Runnable {
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
                            this@QuartzScheduler.runners.remove(task.taskId)
                        }
                    }
                }
            }
        })

        handle(task, 0L)


        var taskPending = head.next
        while (taskPending != null) {
            if (taskPending === task) {
                break
            }
            if (taskPending.taskId == -1 && taskPending.owner == plugin) {
                taskPending.cancel0()
            }
            taskPending = taskPending.next
        }

        runners.values.forEach {
            if (it.owner == plugin) {
                it.cancel0()
            }
        }
    }

    override fun isCurrentlyRunning(taskId: Int): Boolean {
        if (isAsync) {
            if (asyncScheduler.isCurrentlyRunning(taskId)) {
                return true
            }
        }

        val task = runners[taskId] ?: return false

        if (task.isSync) {
            return task == currentTask
        }

        val asyncTask = task as QuartzAsyncTask
        synchronized(asyncTask.workers) {
            return !asyncTask.workers.isEmpty()
        }
    }

    override fun isQueued(taskId: Int): Boolean {
        if (taskId <= 0) {
            return false
        }

        if (!isAsync && asyncScheduler.isQueued(taskId)) {
            return true
        }

        var task = head.next
        while (task != null) {
            if (task.taskId == taskId) {
                return task.period >= QuartzTask.NO_REPEATING
            }
            task = task.next
        }

        val task2 = runners[taskId]

        return (task2 != null) && (task2.period >= QuartzTask.NO_REPEATING)
    }

    override fun getActiveWorkers(): MutableList<BukkitWorker> {
        if (!isAsync) {
            return asyncScheduler.activeWorkers
        }

        val workers = ArrayList<BukkitWorker>()

        for (t in runners.values) {
            if (t.isSync) {
                continue
            }

            val task = t as QuartzAsyncTask

            synchronized(task.workers) {
                workers.addAll(task.workers)
            }
        }

        return workers
    }

    override fun getPendingTasks(): MutableList<BukkitTask> {
        val truePending = ArrayList<QuartzTask>()
        var t = head.next
        while ( t != null) {
            if (t.taskId != -1) {
                truePending.add(t)
            }
            t = t.next
        }

        val pending: MutableList<BukkitTask> = runners.values.filter {
            it.period >= QuartzTask.NO_REPEATING
        }.toMutableList()

        truePending.forEach {
            if (it.period >= QuartzTask.NO_REPEATING && !pending.contains(it)) {
                pending.add(it)
            }
        }

        if (!isAsync) {
            pending.addAll(asyncScheduler.pendingTasks)
        }

        return pending
    }

    override fun runTask(plugin: Plugin, task: Runnable): BukkitTask =
        runTaskLater(plugin, task, 0L)

    override fun runTask(plugin: Plugin, task: Consumer<BukkitTask>) =
        runTaskLater(plugin, task, 0L)

    @Deprecated("Unsupported")
    override fun runTask(plugin: Plugin, task: BukkitRunnable): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTask(Plugin)")
    }

    override fun runTaskAsynchronously(plugin: Plugin, task: Runnable): BukkitTask {
        TODO("Not yet implemented")
    }

    override fun runTaskAsynchronously(plugin: Plugin, task: Consumer<BukkitTask>) {
        TODO("Not yet implemented")
    }

    @Deprecated("Unsupported")
    override fun runTaskAsynchronously(plugin: Plugin, task: BukkitRunnable): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskAsynchronously(Plugin)")
    }

    override fun runTaskLater(plugin: Plugin, task: Runnable, delay: Long): BukkitTask =
        runTaskTimer(plugin, task, delay, QuartzTask.NO_REPEATING)

    override fun runTaskLater(plugin: Plugin, task: Consumer<BukkitTask>, delay: Long) =
        runTaskTimer(plugin, task, delay, QuartzTask.NO_REPEATING)

    @Deprecated("Unsupported")
    override fun runTaskLater(plugin: Plugin, task: BukkitRunnable, delay: Long): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskLater(Plugin, long)")
    }

    override fun runTaskLaterAsynchronously(plugin: Plugin, task: Runnable, delay: Long): BukkitTask {
        TODO("Not yet implemented")
    }

    override fun runTaskLaterAsynchronously(plugin: Plugin, task: Consumer<BukkitTask>, delay: Long) {
        TODO("Not yet implemented")
    }

    @Deprecated("Unsupported")
    override fun runTaskLaterAsynchronously(plugin: Plugin, task: BukkitRunnable, delay: Long): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskLaterAsynchronously(Plugin, long)")
    }

    override fun runTaskTimer(plugin: Plugin, task: Runnable, delay: Long, period: Long) =
        runTaskTimer(plugin, task as Any, delay, period)

    override fun runTaskTimer(plugin: Plugin, task: Consumer<BukkitTask>, delay: Long, period: Long) {
        runTaskTimer(plugin, task as Any, delay, period)
    }

    @Deprecated("Unsupported")
    override fun runTaskTimer(plugin: Plugin, task: BukkitRunnable, delay: Long, period: Long): BukkitTask {
        throw UnsupportedOperationException("Use BukkitRunnable#runTaskTimer(Plugin, long, long)")
    }

    override fun runTaskTimerAsynchronously(plugin: Plugin, task: Runnable, delay: Long, period: Long) =
        runTaskTimerAsynchronously(plugin, task as Any, delay, period)

    override fun runTaskTimerAsynchronously(plugin: Plugin, task: Consumer<BukkitTask>, delay: Long, period: Long) {
        TODO("Not yet implemented")
    }

    override fun runTaskTimerAsynchronously(
        plugin: Plugin,
        task: BukkitRunnable,
        delay: Long,
        period: Long
    ): BukkitTask {
        TODO("Not yet implemented")
    }

    override fun getMainThreadExecutor(plugin: Plugin): Executor {
        Validate.notNull(plugin, "Plugin cannot be null")
        return Executor {
            Validate.notNull(it, "Command cannot be null")
            runTask(plugin, it)
        }
    }

    fun runTaskTimerAsynchronously(plugin: Plugin, task: Any, delay: Long, period: Long): BukkitTask {
        validate(plugin, task)
        val d = max(0L, delay)

        val p = if (period == QuartzTask.ERROR) {
            1L
        } else if (period < QuartzTask.NO_REPEATING) {
            QuartzTask.NO_REPEATING
        } else {
            period
        }

        return handle(QuartzAsyncTask(asyncScheduler.runners, plugin, task, nextId().toLong(), p), d)
    }

    fun runTaskTimer(plugin: Plugin, runnable: Any, delay: Long, period: Long): BukkitTask {
        val d = max(0L, delay)

        val p = if (period == QuartzTask.ERROR) {
            1L
        } else if (period < QuartzTask.NO_REPEATING) {
            QuartzTask.NO_REPEATING
        } else {
            period
        }

        return handle(QuartzTask(plugin, runnable, nextId().toLong(), p), d)
    }

    protected fun handle(task: QuartzTask, delay: Long): QuartzTask {
        if (!this.isAsync && !task.isSync) {
            this.asyncScheduler.handle(task, delay)
            return task
        }

        task.nextRun = currentTick + delay
        addTask(task)
        return task
    }

    protected fun addTask(task: QuartzTask) {
        val tail = this.tail
        var tailTask = tail.get()
        while(!tail.compareAndSet(tailTask, task)) {
            tailTask = tail.get()
        }
        tailTask.next = task
    }

    private fun nextId(): Int {
        Validate.isTrue(
            runners.size < Int.MAX_VALUE,
            "There are already ${Int.MAX_VALUE} tasks scheduled! Cannot schedule more."
        )
        var id: Int
        do {
            id = ids.updateAndGet(INCREMENT_IDS)
        } while (runners.containsKey(id)) // Avoid generating duplicate IDs
        return id
    }

    companion object {
        private const val START_ID = 1

        const val RECENT_TICK = 30

        private val INCREMENT_IDS = IntUnaryOperator { pre ->
            if (pre == Int.MAX_VALUE) {
                START_ID
            }

            pre + 1
        }

        private fun validate(plugin: Plugin, task: Any) {
            Validate.notNull(plugin, "Plugin cannot be null")
            Validate.notNull(task, "Task cannot be null")
            Validate.isTrue(
                task is Runnable || task is Consumer<*> || task is Callable<*>,
                "Task must be Runnable, Consumer, or Callable"
            )
            if (!plugin.isEnabled) {
                throw IllegalPluginAccessException("Plugin attempted to register task while disabled")
            }
        }
    }
}
