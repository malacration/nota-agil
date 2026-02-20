package br.andrew.nota.agil.scheduling

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.scheduling.core.ScheduledAbstract
import br.andrew.nota.agil.services.TaskService
import br.andrew.nota.agil.infrastructure.JobsConfiguration
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Duration


@Component
class SendToWorkFlowSchedule(
    task : TaskScheduler,
    jobStateRepository: JobStateRepository,
    jobsConfiguration: JobsConfiguration,
    val taskRepository: TaskRepository,
    val taskService: TaskService) : ScheduledAbstract(
    task,
    Company("all", "all"),
    JobsTypes.SendWorkFlow,
    jobStateRepository,
    jobsConfiguration,
    Duration.ofMinutes(5)){

    override fun doWork() {
        println("Iniciando envio de tasks para o workflow")
        val tasks = taskRepository.findAllByStatus(TaskStatus.READY)
        println("Encontrado ${tasks.size} pendens de envio")
        tasks.forEach {
            taskService.executa(it)
        }
        println("Finalizando job de envio de tasks")
    }
}
