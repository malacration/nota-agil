package br.andrew.nota.agil.scheduling.core

import br.andrew.nota.agil.controllers.JobStatus
import br.andrew.nota.agil.infrastructure.JobsConfiguration
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobState
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.repository.JobStateRepository
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.ScheduledFuture

abstract class ScheduledAbstract(
    private val scheduler: TaskScheduler,
    val company : Company,
    val jobsTypes : JobsTypes,
    val jobStateRepository: JobStateRepository,
    private val jobsConfiguration: JobsConfiguration,
    initialPeriod: Duration = Duration.ofSeconds(60),
    initialMode: Mode = Mode.FIXED_RATE
) {
    enum class Mode { FIXED_RATE, FIXED_DELAY }
    open val id: String = (this::class.simpleName ?: UUID.randomUUID().toString())+"-"+company.cnpj

    @Volatile private var lastRun: Instant? = null
    protected fun markRun() { lastRun = Instant.now() }
    fun lastRunAt(): Instant? = lastRun

    @Volatile private var future: ScheduledFuture<*>? = null
    @Volatile private var period: Duration = initialPeriod
    @Volatile private var mode: Mode = initialMode
    fun lastRunAsDate(): Date? = lastRun?.let(Date::from) // Instant -> Date


    init {
        val state = getJobState()
        if (state.enabled && !jobsConfiguration.disableAll) start()
    }

    @Synchronized
    fun start(initialDelay: Duration = period) {
        if (jobsConfiguration.disableAll) return
        updateEnabled(true)
        if (isRunning()) return
        future = scheduleInternal(initialDelay)
    }

    @Synchronized
    fun stop() {
        future?.cancel(false)
        future = null
        updateEnabled(false)
    }

    fun runOnce() = tick()

    fun isRunning(): Boolean =
        future?.let { !it.isCancelled && !it.isDone } ?: false

    @Synchronized
    fun updatePeriod(newPeriod: Duration, restartIfRunning: Boolean = true) {
        period = newPeriod
        if (restartIfRunning && isRunning()) {
            stop()
            start()
        }
    }

    @Synchronized
    fun updateMode(newMode: Mode, restartIfRunning: Boolean = true) {
        mode = newMode
        if (restartIfRunning && isRunning()) {
            stop()
            start()
        }
    }

    /** Consulta o período atual (útil para expor em um endpoint). */
    fun currentPeriod(): Duration = period

    /** Consulta o modo atual. */
    fun currentMode(): Mode = mode

    private fun scheduleInternal(initialDelay: Duration): ScheduledFuture<*> {
        val task = Runnable { tick() }
        val startDate: Date? =
            if (initialDelay.isZero) null
            else Date.from(Instant.now().plus(initialDelay))

        return when (mode) {
            Mode.FIXED_RATE ->
                if (startDate == null)
                    scheduler.scheduleAtFixedRate(task, period)
                else
                    scheduler.scheduleAtFixedRate(task, startDate.toInstant(), period)

            Mode.FIXED_DELAY ->
                if (startDate == null)
                    scheduler.scheduleWithFixedDelay(task, period)
                else
                    scheduler.scheduleWithFixedDelay(task, startDate.toInstant(), period)
        }
    }

    protected fun tick() {
        if (jobsConfiguration.disableAll) return
        try { doWork() } finally { markRun() }
    }

    protected abstract fun doWork()

    fun toStatus(): JobStatus {
        return JobStatus(
            this.id,
            company,
            this.isRunning(),
            this.currentPeriod().toMinutes(),
            this.currentMode().name,
            lastRunAsDate(),
            getJobState()
        )

    }

    fun getJobState(): JobState {
        return jobStateRepository
            .findByEmpresaAndTipo(company.cnpj,jobsTypes)
            ?: jobStateRepository.save(JobState(company.cnpj,jobsTypes))
    }

    private fun updateEnabled(enabled: Boolean) {
        val state = getJobState()
        if (state.enabled == enabled) return
        state.enabled = enabled
        jobStateRepository.save(state)
    }

    /** Sobrescreva se quiser logar/tratar erros. */
    protected open fun handleError(t: Throwable) {

    }
}
