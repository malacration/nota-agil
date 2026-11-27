package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.erp.*
import br.andrew.nota.agil.model.tasks.*
import br.andrew.nota.agil.repository.CompanyRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import org.springframework.dao.DuplicateKeyException

/** Integra o protocolo ERP a fila existente, preservando pausas manuais e idempotencia. */
@Component
class ConsultaErpTaskQueue(private val mongo: MongoTemplate, private val empresas: CompanyRepository) {
    fun sincronizar(consulta: ConsultaErpTask) {
        val id = "${consulta.id}-${TaskTypes.ConsultaErp}"
        val tomador = empresas.findById(consulta.requisicao.cpfCnpjTomador).orElse(null)?.nome
            ?.takeIf { it.isNotBlank() } ?: consulta.requisicao.cpfCnpjTomador
        val update = Update()
            .setOnInsert("name", "Pagamento ${consulta.requisicao.workflowId} / ${consulta.requisicao.solicitacaoId}")
            .setOnInsert("taskType", TaskTypes.ConsultaErp)
            .setOnInsert("createdAt", consulta.criadaEm)
            .set("recordID", consulta.requisicao.workflowId)
            .set("consultaErp", ConsultaErpListItem.from(consulta))
            .set("tomador", tomador)
        when (consulta.status) {
            StatusConsultaErp.CONCLUIDA -> update.set("status", TaskStatus.FINISHED)
            // Entrega esgotada: pausa na fila para tratativa manual; READY manual reativa.
            StatusConsultaErp.RETORNO_FALHOU -> update.set("status", TaskStatus.FAILED)
            // Demais: preserva pausa manual (FAILED) de uma task ja existente.
            else -> update.setOnInsert("status", TaskStatus.READY)
        }
        val criteria = Criteria.where("_id").`is`(id).andOperator(Criteria().orOperator(
            Criteria.where("consultaErp.atualizadaEm").exists(false),
            Criteria.where("consultaErp.atualizadaEm").lte(consulta.atualizadaEm),
        ))
        if (consulta.status != StatusConsultaErp.CONCLUIDA) criteria.and("status").ne(TaskStatus.FINISHED)
        try {
            mongo.upsert(Query(criteria), update, Task::class.java)
        } catch (error: DuplicateKeyException) {
            // Outra execucao ja inseriu/concluiu ou atualizou uma versao mais nova.
            if (mongo.findById(id, Task::class.java)?.taskType != TaskTypes.ConsultaErp) throw error
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun recuperarRegistrosExistentes() {
        mongo.stream(Query().cursorBatchSize(100), ConsultaErpTask::class.java).use { registros ->
            registros.forEach(::sincronizar)
        }
    }
}
