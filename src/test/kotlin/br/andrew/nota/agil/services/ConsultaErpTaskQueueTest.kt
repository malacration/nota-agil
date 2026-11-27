package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.erp.ConsultaErpTask
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.model.erp.StatusConsultaErp
import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.repository.CompanyRepository
import br.andrew.nota.agil.support.anyValue as any
import com.mongodb.client.result.UpdateResult
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Update
import java.math.BigDecimal
import java.util.Optional

/** Mapeamento status do protocolo -> status da task nativa (sem MongoDB real). */
class ConsultaErpTaskQueueTest {
    private val mongo = mock(MongoTemplate::class.java).also {
        `when`(it.upsert(any(), any(), eq(Task::class.java))).thenReturn(UpdateResult.acknowledged(1, 1, null))
    }
    private val empresas = mock(CompanyRepository::class.java).also {
        `when`(it.findById(anyString())).thenReturn(Optional.empty())
    }
    private val queue = ConsultaErpTaskQueue(mongo, empresas)
    private val protocolo = ConsultaErpTask("p", RegistrarConsultaErpRequest("WF1", "s", "12345678000190",
        "98765432000100", "001", BigDecimal.TEN), "atividade", 1, 2, "tabela")

    private fun update(status: StatusConsultaErp): Document {
        queue.sincronizar(protocolo.copy(status = status))
        val captor = ArgumentCaptor.forClass(Update::class.java)
        verify(mongo).upsert(any(), captor.capture() ?: Update(), eq(Task::class.java))
        return captor.value.updateObject
    }

    private fun Document.campo(operador: String, campo: String) = (get(operador) as Document?)?.get(campo)

    @Test
    fun `entrega esgotada pausa a task nativa como FAILED`() {
        val doc = update(StatusConsultaErp.RETORNO_FALHOU)
        assertEquals(TaskStatus.FAILED, doc.campo("\$set", "status"))
        assertNull(doc.campo("\$setOnInsert", "status"))
    }

    @Test
    fun `retorno pendente preserva pausa manual`() {
        val pendente = update(StatusConsultaErp.RETORNO_PENDENTE)
        assertNull(pendente.campo("\$set", "status"))
        assertEquals(TaskStatus.READY, pendente.campo("\$setOnInsert", "status"))
    }

    @Test
    fun `concluida finaliza a task nativa`() {
        assertEquals(TaskStatus.FINISHED, update(StatusConsultaErp.CONCLUIDA).campo("\$set", "status"))
    }
}
