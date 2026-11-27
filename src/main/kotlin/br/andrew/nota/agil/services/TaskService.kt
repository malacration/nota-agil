package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.sap.SapNotasEntradaClient
import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.erp.ConsultaErpListItem
import br.andrew.nota.agil.model.tasks.OrigemNota
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
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@Service
@EnableConfigurationProperties(WorkFlowEnvrioment::class)
class TaskService(
    val flow: WorkFlowEnvrioment,
    val workFlowService: WorkFlowService,
    val taskRepository: TaskRepository,
    val documentoService: DocumentService,
    val qiveApi : QiveApiClient,
    private val consultaErpService: ConsultaErpService,
    private val sapNotas: SapNotasEntradaClient,
    private val qivePdf: QivePdfLocator,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    fun updateStatus(id: String, newStatus: TaskStatus): Task {
        val task = taskRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Task $id nao encontrada") }
        val currentStatus = task.status

        if (currentStatus == newStatus) {
            return task
        }

        try {
            task.changeStatus(newStatus)
        } catch (e: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        // ConsultaErp com entrega ao SE esgotada (RETORNO_FALHOU): reativa o protocolo ANTES de a
        // task voltar a READY, para o job nunca ver READY com o protocolo ainda esgotado. Pausa
        // manual comum (protocolo em outro status) segue so com a troca de status.
        if (task.taskType == TaskTypes.ConsultaErp && newStatus == TaskStatus.READY) {
            task.consultaErp?.let { consulta ->
                consultaErpService.reativarRetorno(consulta.id)?.let { task.consultaErp = ConsultaErpListItem.from(it) }
            }
        }

        return taskRepository.save(task)
    }

    fun executa(task : Task){
        if(task.status == TaskStatus.FINISHED){
            return
        }

        if (task.taskType == TaskTypes.ConsultaErp) {
            if (task.status != TaskStatus.READY) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Marque a task como READY antes de executar")
            }
            val consulta = task.consultaErp ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Task ERP sem protocolo")
            consultaErpService.executar(consulta.id)
            return
        }

        if(task.taskType == TaskTypes.CreateTask)
            executaCreateTask(task)
        if(task.taskType == TaskTypes.UploadPdf)
            executaUploadTask(task)
        if(task.taskType == TaskTypes.UploadPdfErp)
            executaUploadErpTask(task)
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
            duplicata.getSoftexpertMap(flow.tableName, flow.campoSerie, flow.campoChaveAcesso)
        )
        if(dadosFluxo.status == "SUCCESS"){
            task.recordKey = dadosFluxo.recordKey
            task.recordID = dadosFluxo.recordID
            task.status = TaskStatus.FINISHED
            val parser = TasksParser(task)
            taskRepository.saveAll(listOf(
                task,
                // Nota importada de ERP: anexo pela UploadPdfErp (Qive -> [SAP: anexo do SAP] -> sem anexo).
                if (task.notaErp != null) parser.toTaskUploadErp() else parser.toTaskUploadDocument()
            ))
        }else{
            task.status = TaskStatus.FAILED
            taskRepository.saveAll(listOf(
                task,
            ))
            throw Exception("Erro ao criar a tarefa. ${dadosFluxo.detail}")
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
        }else{
            task.status = TaskStatus.FAILED
            taskRepository.saveAll(listOf(
                task,
            ))
            throw Exception("Erro ao mover a tarefa. ${dadosFluxo.detail}")
        }
    }

    /**
     * Anexo de nota importada de ERP. Diferente da [executaUploadTask] (so Qive pelo id, falha sem PDF):
     * 1. Qive ([QivePdfLocator]): pela chave de acesso ou procurando a nota recebida (prestador + numero);
     * 2. so SAP: PDF anexado a nota no SAP (Attachments2, via sap-rovema). Dealer nao tem este passo;
     * 3. nada encontrado: segue SEM anexo, so executando a atividade no SE.
     * Falha no SE (gravar documento, associar ou executar a atividade) continua marcando FAILED.
     */
    private fun executaUploadErpTask(task: Task) {
        if(task.status != TaskStatus.READY)
            throw Exception("Nao é permitido reexecutar tasks. ${task.id}")
        val duplicata = task.duplicata ?: throw Exception("Duplicata nao pode ser null")
        val workflowId = task.recordID ?: throw Exception("Nao é possivel fazer upload sem workflowID")

        val (origem, pdf) = qivePdf.buscar(duplicata)?.let { "QIVE" to it }
            ?: pdfDoSap(task)?.let { "SAP" to it }
            ?: ("NENHUM" to null)

        try {
            if (pdf != null) {
                val documento = documentoService.save(pdf, task.name, flow.docCategory)
                val documentoId = documento?.recordID ?: throw Exception("Nao e possivel associar documento pois o ID do retorno essta null")
                workFlowService.associa(flow.idAtividadeUpload, workflowId, documentoId)
            }
            val dadosFluxo = workFlowService.executaAtividade(workflowId, flow.idAtividadeUpload)
            if (dadosFluxo.status != "SUCCESS") throw Exception("Erro ao mover a tarefa. ${dadosFluxo.detail}")
        } catch (e: Exception) {
            task.status = TaskStatus.FAILED
            taskRepository.save(task)
            throw e
        }
        task.origemAnexo = origem
        task.status = TaskStatus.FINISHED
        taskRepository.save(task)
    }

    private fun pdfDoSap(task: Task): ByteArray? {
        val nota = task.notaErp?.takeIf { it.erp == Erp.SAP } ?: return null
        val docEntry = nota.id.toIntOrNull() ?: return null
        return try {
            sapNotas.anexoPdf(docEntry, nota.cnpjFilial)
        } catch (e: Exception) {
            log.warn("PDF da nota nao obtido no SAP ({}); seguindo sem anexo", e.javaClass.simpleName)
            null
        }
    }
}
