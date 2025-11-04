package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.Task
import br.andrew.nota.agil.model.TaskStatus
import br.andrew.nota.agil.model.TaskTypes
import br.andrew.nota.agil.qive.infrastructure.QiveConfiguration
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.softexpert.service.WorkFlowEnvrioment
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service

@Service
@EnableConfigurationProperties(WorkFlowEnvrioment::class)
class TaskService(
    //TODO colocar id do processo no construtor
    val flow: WorkFlowEnvrioment,
    val workFlowService: WorkFlowService,
    val taskRepository: TaskRepository,

) {
    fun executa(task : Task){
        if(task.status != TaskStatus.READY){
            return
        }

        if(task.taskType == TaskTypes.CreateTask)
            executaCreateTask(task)
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
            taskRepository.save(task.also {
                it.status = TaskStatus.FINISHED
            })
        }
    }
}