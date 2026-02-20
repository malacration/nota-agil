package br.andrew.nota.agil.scheduling.generic

import br.andrew.nota.agil.infrastructure.JobsConfiguration
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.scheduling.core.NfReciveGeneric
import org.springframework.scheduling.TaskScheduler
import java.time.Duration

class CteReciveSchedule(
    task: TaskScheduler,
    company: Company,
    jobStateRepository: JobStateRepository,
    jobsConfiguration: JobsConfiguration,
    val taskRepository: TaskRepository,
    val service: QiveApiClient,
) : NfReciveGeneric(
    task,
    company,
    JobsTypes.CteReceived,
    jobStateRepository,
    jobsConfiguration,
    Duration.ofMinutes(1),
) {
    override fun doWork() {
        val state = getJobState()
        val fromDate = getFromDate(state)
        val toDate = getToDate(state)
        var cursor: Int = state.cursor

        do {
            val resposta = service.cte.listReceived(listOf(company.cnpj), cursor, fromDate, toDate)
            resposta.data.forEach {
                println("Criando task para CTe encontrado")
                val duplicata = it.getDuplicata()
                val task = Task(
                    it.id + "-" + TaskTypes.CreateTask,
                    duplicata.getTitulo(),
                    TaskStatus.READY,
                    TaskTypes.CreateTask,
                    duplicata,
                )
                if (taskRepository.findById(task.id).isEmpty) {
                    taskRepository.save(task)
                }
            }
            cursor = resposta.page?.getNextCursor() ?: cursor++
            state.cursor = cursor
            jobStateRepository.save(state)
        } while (!(resposta.page?.isEnd() ?: true))

        println("job executado para ${company.nome}")

        state.lastFrom = toDate.toInstant()
        state.cursor = 0
        jobStateRepository.save(state)
    }
}
