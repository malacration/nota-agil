package br.andrew.nota.agil.scheduling

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.model.TaskStatus
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.scheduling.core.ScheduledAbstract
import br.andrew.nota.agil.services.TaskService
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Duration


@Component
class SendToWorkFlowSchedule(
    task : TaskScheduler,
    jobStateRepository: JobStateRepository,
    val taskRepository: TaskRepository,
    val taskService: TaskService) : ScheduledAbstract(
    task,
    Company("all", "all"),
    JobsTypes.SendWorkFlow,
    jobStateRepository,
    Duration.ofMinutes(5)){

    init {
        this.stop()
    }

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