package br.andrew.nota.agil.scheduling

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.scheduling.core.ScheduledAbstract
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component


@Component
class TesteBSchedule(task : TaskScheduler, jobStateRepository: JobStateRepository) : ScheduledAbstract(
    task,
    Company("all", "all"),
    JobsTypes.NfeEvents,
    jobStateRepository){

    override fun doWork() {
        println("pedro")
    }
}