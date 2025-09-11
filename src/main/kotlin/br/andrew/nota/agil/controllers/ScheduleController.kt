package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobState
import br.andrew.nota.agil.scheduling.core.ScheduledAbstract
import br.andrew.nota.agil.services.DynamicScheduler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.Date

@RestController
@RequestMapping("/schedules")
class ScheduleController(
    private val scheduler: DynamicScheduler,
    private val jobsProvider: ObjectProvider<ScheduledAbstract>,
) {

    val jobs : List<ScheduledAbstract>
        get(){
            return jobsProvider.orderedStream().toList()
        }


    private val registry: Map<String, ScheduledAbstract>
        get() {
            return jobs.associateBy { it.id }.also { map ->
                // Sanidade: ids duplicados
                require(map.size == jobs.size) {
                    val dups = jobs.groupBy { it.id }.filterValues { it.size > 1 }.keys
                    "IDs duplicados em br.andrew.nota.agil.scheduling.core.ScheduledAbstract: $dups"
                }
            }
        }


    // -------- helpers
    private fun job(id: String): ScheduledAbstract =
        registry[id] ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job '$id' não encontrado")

    @GetMapping
    fun list(): List<JobStatus> = registry.values.map { it.toStatus() }.sortedBy { it.id }

    @GetMapping("/{id}")
    fun status(@PathVariable id: String): JobStatus = job(id).toStatus()

    @GetMapping("/{id}/start")
    fun start(
        @PathVariable id: String,
        @RequestParam(required = false) delayMs: Long?
    ): JobStatus {
        val j = job(id)
        j.start(Duration.ofMillis(delayMs ?: 0))
        return j.toStatus()
    }

    @GetMapping("/{id}/stop")
    fun stop(@PathVariable id: String): JobStatus {
        val j = job(id)
        j.stop()
        return j.toStatus()
    }

    @GetMapping("/{id}/run-once")
    fun runOnce(@PathVariable id: String) = mapOf("triggered" to { job(id).runOnce(); true }())

    @GetMapping("/{id}/period")
    fun updatePeriod(
        @PathVariable id: String,
        @RequestParam minute: Long,
        @RequestParam(required = false, defaultValue = "true") restartIfRunning: Boolean
    ): JobStatus {
        val j = job(id)
        j.updatePeriod(Duration.ofMinutes(minute), restartIfRunning)
        return j.toStatus()
    }

    @GetMapping("/{id}/mode")
    fun updateMode(
        @PathVariable id: String,
        @RequestParam mode: String,
        @RequestParam(required = false, defaultValue = "true") restartIfRunning: Boolean
    ): JobStatus {
        val j = job(id)
        j.updateMode(ScheduledAbstract.Mode.valueOf(mode.uppercase()), restartIfRunning)
        return j.toStatus()
    }

    @GetMapping("/start-all")
    fun startAll() =
        registry.values.onEach { it.start() }
            .map { it.toStatus() }

    @PostMapping("/stop-all")
    fun stopAll() =
        registry.values.onEach { it.stop() }.map { it.toStatus() }
}

data class JobStatus(
    val id: String,
    val company: Company,
    val running: Boolean,
    val period: Long,
    val mode: String,
    val lastRun: Date?,
    val jobState: JobState
)