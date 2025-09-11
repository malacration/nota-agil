package br.andrew.nota.agil.scheduling.core

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobState
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.repository.TaskRepository
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
    initialPeriod: Duration = Duration.ofSeconds(60),
    initialMode: Mode = Mode.FIXED_RATE
) : ScheduledAbstract(
    task,
    company,
    JobsTypes,
    jobStateRepository,
    initialPeriod,
    initialMode
){
    fun getFromDate(state : JobState) : Date {
        return Date.from(state.lastFrom)
    }

    fun getToDate(state : JobState) : Date {
        val toInstant: Instant = state.lastFrom.plus(1, ChronoUnit.HOURS)
        val now = Instant.now()
        if (toInstant.isAfter(now)) {
            throw Exception("job ${company.nome}: esta pegando uma data considerada no futuro.")
        }
        return Date.from(toInstant)
    }
}