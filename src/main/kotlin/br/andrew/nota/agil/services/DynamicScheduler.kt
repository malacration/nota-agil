package br.andrew.nota.agil.services

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Service
class DynamicScheduler(private val scheduler: ThreadPoolTaskScheduler) {

    private val tasks = ConcurrentHashMap<String, Runnable>()
    private val futures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    fun register(name: String, task: Runnable) { tasks[name] = task }

    fun startCron(name: String, cron: String, zoneId: ZoneId = ZoneId.of("America/Porto_Velho")) {
        stop(name)
        val task = tasks[name] ?: error("Tarefa '$name' não registrada")
        val trigger = CronTrigger(cron, TimeZone.getTimeZone(zoneId))
        val future = scheduler.schedule(task, trigger)
            ?: error("Falha ao agendar '$name'")
        futures[name] = future
    }

    fun startFixedRate(name: String, period: Duration) {
        stop(name)
        val task = tasks[name] ?: error("Tarefa '$name' não registrada")
        val future = scheduler.scheduleAtFixedRate(task, period)
        futures[name] = future
    }

    fun stop(name: String): Boolean =
        futures.remove(name)?.cancel(true) ?: false

    fun isRunning(name: String) = futures[name]?.let { !it.isCancelled && !it.isDone } ?: false

    fun status(): Map<String, Any> =
        tasks.keys.associateWith { isRunning(it) }
}
