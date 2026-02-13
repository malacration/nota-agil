package br.andrew.nota.agil.scheduling

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.scheduling.core.ScheduledAbstract
import br.andrew.nota.agil.infrastructure.JobsConfiguration
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component


@Component
class TesteBSchedule(
    task : TaskScheduler,
    jobStateRepository: JobStateRepository,
    jobsConfiguration: JobsConfiguration
) : ScheduledAbstract(
    task,
    Company("all", "all"),
    JobsTypes.NfeEvents,
    jobStateRepository,
    jobsConfiguration){

    override fun doWork() {
        println("pedro")
    }
}
