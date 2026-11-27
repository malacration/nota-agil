package br.andrew.nota.agil.scheduling.generic

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.JobsTypes
import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.JobStateRepository
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.services.NotasErpService
import br.andrew.nota.agil.services.TravaNotas
import br.andrew.nota.agil.scheduling.core.NfReciveGeneric
import br.andrew.nota.agil.infrastructure.JobsConfiguration
import org.springframework.scheduling.TaskScheduler
import java.time.Duration


class NfseReciveSchedule(
        task : TaskScheduler,
        company : Company,
        jobStateRepository: JobStateRepository,
        jobsConfiguration: JobsConfiguration,
        val taskRepository: TaskRepository,
        val service: QiveApiClient,
        val trava: TravaNotas) : NfReciveGeneric(
    task,
    company,
    JobsTypes.NfseReceived,
    jobStateRepository,
    jobsConfiguration,
    Duration.ofMinutes(1)
){

    override fun doWork() {
        val state = getJobState()
        val fromDate = getFromDate(state)
        val toDate = getToDate(state)
        var cursor: Int = state.cursor

        do {
            val resposta = service.nfse.listReceived(listOf(company.cnpj), cursor, fromDate, toDate)
            resposta.data.forEach {
                println("Criando task para nota encontrada")
                val duplicata = it.getDuplicata()
                val task = Task(it.id+"-"+TaskTypes.CreateTask,duplicata.getTitulo(), TaskStatus.READY,TaskTypes.CreateTask, duplicata)
                // insert (nunca save) dentro da trava: nao sobrescreve task de ERP de mesmo id nem cria
                // outra para nota ja importada de ERP (SAP/Dealer, com outro id).
                NotasErpService.criarTaskDoQive(taskRepository, trava, task)
            }
            cursor = resposta.page?.getNextCursor() ?: cursor++
            state.cursor = cursor
            salvarProgresso(state)
        }while (!(resposta.page?.isEnd() ?: true))

        println("job executado para ${company.nome}")

        state.lastFrom = toDate.toInstant()
        state.cursor = 0
        salvarProgresso(state)

    }
}
