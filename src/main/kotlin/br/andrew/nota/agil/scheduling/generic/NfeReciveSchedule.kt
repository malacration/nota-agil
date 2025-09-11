package br.andrew.nota.agil.scheduling.generic

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.model.Task
import br.andrew.nota.agil.model.TaskStatus
import br.andrew.nota.agil.model.TaskTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.scheduling.core.NfReciveGeneric
import org.springframework.scheduling.TaskScheduler
import java.time.Duration


class NfeReciveSchedule(
        task : TaskScheduler,
        company : Company,
        jobStateRepository: JobStateRepository,
        val taskRepository: TaskRepository,
        val service: QiveApiClient) : NfReciveGeneric(
    task,
    company,
    JobsTypes.NfeReceived,
    jobStateRepository,
    Duration.ofMinutes(1)
){
    init {
        stop()
    }

    override fun doWork() {
        val state = getJobState()
        val fromDate = getFromDate(state)
        val toDate = getToDate(state)
        var cursor: Int = state.cursor

        do {
            val resposta = service.nfe.listReceived(listOf(company.cnpj), cursor, fromDate, toDate)
            resposta.data.forEach {
                println("Criando task para nota encontrada")
                val duplicata = it.getDuplicata()
                val task = Task(it.id+"-"+TaskTypes.CreateTask,duplicata.getTitulo(), TaskStatus.READY,TaskTypes.CreateTask, duplicata)
                if(taskRepository.findById(task.id).isEmpty)
                    taskRepository.save(task)
            }
            cursor = resposta.page?.getNextCursor() ?: cursor++
            state.cursor = cursor
            jobStateRepository.save(state)
        }while (!(resposta.page?.isEnd() ?: true))

        println("job executado para ${company.nome}")

        state.lastFrom = toDate.toInstant()
        state.cursor = 0
        jobStateRepository.save(state)

    }
}