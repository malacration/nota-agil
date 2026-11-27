package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.erp.ConsultaErpTask
import br.andrew.nota.agil.model.erp.StatusConsultaErp
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Repository
class ConsultaErpRepository(private val mongo: MongoTemplate) {
    fun buscar(id: String): ConsultaErpTask? = mongo.findById(id, ConsultaErpTask::class.java)

    fun listar(page: Pageable, status: StatusConsultaErp?, workflowId: String?): Page<ConsultaErpTask> {
        val query = Query()
        status?.let { query.addCriteria(Criteria.where("status").`is`(it)) }
        workflowId?.let { query.addCriteria(Criteria.where("requisicao.workflowId").`is`(it)) }
        val total = mongo.count(Query.of(query), ConsultaErpTask::class.java)
        return PageImpl(mongo.find(query.with(page), ConsultaErpTask::class.java), page, total)
    }

    fun inserirOuRecuperar(task: ConsultaErpTask): ConsultaErpTask = try {
        mongo.insert(task)
    } catch (e: DuplicateKeyException) {
        buscar(task.id) ?: throw e
    }

    fun reservarProxima(agora: Instant, id: String? = null): ConsultaErpTask? {
        val criteria = Criteria().andOperator(
            Criteria.where("status").`in`(StatusConsultaErp.PENDENTE, StatusConsultaErp.RETORNO_PENDENTE),
            Criteria.where("proximaTentativa").lte(agora),
            Criteria().orOperator(Criteria.where("bloqueadoAte").`is`(null), Criteria.where("bloqueadoAte").lte(agora)),
        )
        val query = Query(criteria).with(Sort.by("proximaTentativa", "criadaEm"))
        id?.let { query.addCriteria(Criteria.where("_id").`is`(it)) }
        return mongo.findAndModify(
            query,
            Update().set("reserva", UUID.randomUUID().toString()).set("bloqueadoAte", agora.plus(Duration.ofMinutes(10))),
            FindAndModifyOptions.options().returnNew(true),
            ConsultaErpTask::class.java,
        )
    }

    fun atualizar(task: ConsultaErpTask, liberar: Boolean = false): ConsultaErpTask {
        check(task.reserva != null) { "Tarefa ERP sem reserva" }
        val query = Query(Criteria.where("_id").`is`(task.id).and("reserva").`is`(task.reserva)
            .and("bloqueadoAte").gt(Instant.now()))
        val update = Update()
            .set("status", task.status).set("erp", task.erp).set("resultado", task.resultado)
            .set("respostaRegistrada", task.respostaRegistrada).set("tentativasRetorno", task.tentativasRetorno)
            .set("ultimoErro", task.ultimoErro).set("atualizadaEm", task.atualizadaEm)
            .set("proximaTentativa", task.proximaTentativa).set("concluidaEm", task.concluidaEm)
            .set("consultas", task.consultas).set("ultimaConsulta", task.ultimaConsulta)
            .set("ultimaConsultaEm", task.ultimaConsultaEm).set("esperaPagamentoAte", task.esperaPagamentoAte)
            .set("falhasTecnicas", task.falhasTecnicas).set("primeiraFalhaTecnicaEm", task.primeiraFalhaTecnicaEm)
            .set("ultimaFalhaTecnica", task.ultimaFalhaTecnica).set("ultimaFalhaTecnicaEm", task.ultimaFalhaTecnicaEm)
            .set("falhasRetorno", task.falhasRetorno).set("primeiraFalhaRetornoEm", task.primeiraFalhaRetornoEm)
        if (liberar) update.set("reserva", null).set("bloqueadoAte", null)
        return checkNotNull(mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), ConsultaErpTask::class.java)) {
            "Reserva da tarefa ERP expirada ou assumida por outro executor"
        }
    }

    /**
     * Retoma a entrega de uma tarefa `RETORNO_FALHOU` (operador voltou a task para READY):
     * `RETORNO_PENDENTE`, nova janela de tentativas e entrega ja liberada. Atomico e idempotente:
     * qualquer outro status devolve null sem alterar nada. `resultado`, `respostaRegistrada` e
     * `ultimoErro` ficam como estao (comentario ja confirmado nao e enviado de novo).
     */
    fun reativarRetorno(id: String, agora: Instant): ConsultaErpTask? = mongo.findAndModify(
        Query(Criteria.where("_id").`is`(id).and("status").`is`(StatusConsultaErp.RETORNO_FALHOU)),
        Update().set("status", StatusConsultaErp.RETORNO_PENDENTE)
            .set("falhasRetorno", 0).set("primeiraFalhaRetornoEm", null)
            .set("proximaTentativa", agora).set("atualizadaEm", agora)
            .set("reserva", null).set("bloqueadoAte", null),
        FindAndModifyOptions.options().returnNew(true),
        ConsultaErpTask::class.java,
    )
}
