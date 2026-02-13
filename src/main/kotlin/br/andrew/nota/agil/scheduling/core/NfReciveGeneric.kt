package br.andrew.nota.agil.scheduling.core

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobState
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.infrastructure.JobsConfiguration
import org.springframework.scheduling.TaskScheduler
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

abstract class NfReciveGeneric(
    task : TaskScheduler,
    company : Company,
    JobsTypes : JobsTypes,
    jobStateRepository: JobStateRepository,
    jobsConfiguration: JobsConfiguration,
    initialPeriod: Duration = Duration.ofSeconds(60),
    initialMode: Mode = Mode.FIXED_RATE
) : ScheduledAbstract(
    task,
    company,
    JobsTypes,
    jobStateRepository,
    jobsConfiguration,
    initialPeriod,
    initialMode
){
    fun getFromDate(state : JobState) : Date {
        return Date.from(state.lastFrom)
    }

    fun getToDate(state : JobState) : Date {
        val maxToInstant: Instant = state.lastFrom.plus(1, ChronoUnit.HOURS)
        val now = Instant.now()
        val toInstant = if (maxToInstant.isAfter(now)) now else maxToInstant
        return Date.from(toInstant)
    }
}
