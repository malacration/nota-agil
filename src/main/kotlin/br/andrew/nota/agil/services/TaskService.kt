package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.model.tasks.TasksParser
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.softexpert.service.DocumentService
import br.andrew.nota.agil.softexpert.service.WorkFlowEnvrioment
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service

@Service
@EnableConfigurationProperties(WorkFlowEnvrioment::class)
class TaskService(
    val flow: WorkFlowEnvrioment,
    val workFlowService: WorkFlowService,
    val taskRepository: TaskRepository,
    val documentoService: DocumentService,
    val qiveApi : QiveApiClient,

) {
    fun executa(task : Task){
        if(task.status != TaskStatus.READY){
            return
        }

        if(task.taskType == TaskTypes.CreateTask)
            executaCreateTask(task)
        if(task.taskType == TaskTypes.UploadPdf)
            executaUploadTask(task)
    }

    private fun executaCreateTask(task : Task){
        if(task.taskType != TaskTypes.CreateTask)
            throw Exception("Nao e possivel criar task de uma task do tipo ${task.taskType}")
        if(task.status != TaskStatus.READY)
            throw Exception("Nao é permitido reexecutar tasks. ${task.id}")
        val duplicata : Duplicata = task.duplicata ?: throw Exception("Erro ao recuperar duplicata")
        val dadosFluxo = workFlowService.instanciaFluxo(
            flow.idProcess,
            duplicata.getTitulo(),
            duplicata.getSoftexpertMap(flow.tableName)
        )
        if(dadosFluxo.status == "SUCCESS"){
            task.recordKey = dadosFluxo.recordKey
            task.recordID = dadosFluxo.recordID
            task.status = TaskStatus.FINISHED
            taskRepository.saveAll(listOf(
                task,
                TasksParser(task).toTaskUploadDocument()
            ))
        }
    }

    private fun executaUploadTask(task: Task) {
        if(task.duplicata == null)
            throw Exception("Duplicata nao pode ser null")

        val tipo = task.duplicata.tipo
        val chaveAcesso : String = task.duplicata.chaveAcesso ?: throw Exception("Chave de acesso nao pode ser null")
        val workflowId = task.recordID ?: throw Exception("Nao é possivel fazer upload sem workflowID")

        val pdf = if(tipo == TipoDuplicata.Nfse)
            qiveApi.nfse.danfse(chaveAcesso)
        else if(tipo == TipoDuplicata.Nfe)
            qiveApi.nfe.danfe(chaveAcesso)
        else if(tipo == TipoDuplicata.Cte)
            qiveApi.cte.dacte(chaveAcesso)
        else
            throw Exception("Tipo de documento não configurado")

        val documento = documentoService.save(pdf,task.name,flow.docCategory)
        val documentoId = documento?.recordID ?: throw Exception("Nao e possivel associar documento pois o ID do retorno essta null")
        workFlowService.associa(flow.idAtividadeUpload,
            workflowId,
            documentoId)
        val dadosFluxo = workFlowService.executaAtividade(workflowId,flow.idAtividadeUpload)
        if(dadosFluxo.status == "SUCCESS"){
            task.status = TaskStatus.FINISHED
            taskRepository.saveAll(listOf(
                task,
            ))
        }
    }
}