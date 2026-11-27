package br.andrew.nota.agil.services

import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.qive.interfaces.controllers.Company
import br.andrew.nota.agil.qive.interfaces.controllers.Cte
import br.andrew.nota.agil.qive.interfaces.controllers.Events
import br.andrew.nota.agil.qive.interfaces.controllers.Nfe
import br.andrew.nota.agil.qive.interfaces.controllers.Nfse
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.softexpert.service.DocumentService
import br.andrew.nota.agil.softexpert.service.WorkFlowEnvrioment
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import br.andrew.wsdl.document.DocumentoPortType
import br.andrew.wsdl.workflow.WorkflowPortType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import br.andrew.nota.agil.model.erp.*
import java.math.BigDecimal

class TaskServiceTest {

    private val repository = mock(TaskRepository::class.java)
    private val consultaErp = mock(ConsultaErpService::class.java)
    private val service = TaskService(
        flow = WorkFlowEnvrioment("process", "table", "doc", "atividade"),
        workFlowService = WorkFlowService(
            mock(WorkflowPortType::class.java),
            DocumentService(mock(DocumentoPortType::class.java)),
        ),
        taskRepository = repository,
        documentoService = DocumentService(mock(DocumentoPortType::class.java)),
        qiveApi = QiveApiClient(
            mock(Nfe::class.java),
            mock(Events::class.java),
            mock(Company::class.java),
            mock(Nfse::class.java),
            mock(Cte::class.java),
        ),
        consultaErpService = consultaErp,
        sapNotas = mock(br.andrew.nota.agil.erp.sap.SapNotasEntradaClient::class.java),
        qivePdf = mock(QivePdfLocator::class.java),
    )

    @Test
    fun `execucao existente deve encaminhar task ERP para o protocolo correto`() {
        val protocolo = ConsultaErpTask("protocolo", RegistrarConsultaErpRequest("WF1", "sol1", "12345678000190",
            "98765432000100", "001", BigDecimal.TEN), "atividade", 1, 2, "tabela")
        val task = Task("protocolo-ConsultaErp", "Pagamento", TaskStatus.READY, TaskTypes.ConsultaErp)
            .also { it.consultaErp = ConsultaErpListItem.from(protocolo) }
        service.executa(task)
        verify(consultaErp).executar("protocolo")
        task.status = TaskStatus.FAILED
        assertThrows<ResponseStatusException> { service.executa(task) }
        task.status = TaskStatus.FINISHED
        service.executa(task)
        verify(consultaErp, org.mockito.Mockito.times(1)).executar("protocolo")
    }

    @Test
    fun `deve alterar task de FAILED para READY`() {
        val task = Task("task-1", "Task teste", TaskStatus.FAILED, TaskTypes.UploadPdf)
        `when`(repository.findById(task.id)).thenReturn(Optional.of(task))
        `when`(repository.save(task)).thenReturn(task)

        val atualizada = service.updateStatus(task.id, TaskStatus.READY)

        assertEquals(TaskStatus.READY, atualizada.status)
        verify(repository).save(task)
    }

    @Test
    fun `deve rejeitar transicao manual invalida`() {
        val task = Task("task-2", "Task teste", TaskStatus.READY, TaskTypes.UploadPdf)
        `when`(repository.findById(task.id)).thenReturn(Optional.of(task))

        val erro = assertThrows<ResponseStatusException> {
            service.updateStatus(task.id, TaskStatus.FINISHED)
        }

        assertEquals(HttpStatus.BAD_REQUEST, erro.statusCode)
        assertEquals(TaskStatus.READY, task.status)
        verify(repository, never()).save(task)
    }

    @Test
    fun `deve retornar 404 quando task nao existir`() {
        `when`(repository.findById("inexistente")).thenReturn(Optional.empty())

        val erro = assertThrows<ResponseStatusException> {
            service.updateStatus("inexistente", TaskStatus.READY)
        }

        assertEquals(HttpStatus.NOT_FOUND, erro.statusCode)
        assertFalse(erro.reason.isNullOrBlank())
    }

    private val protocolo = ConsultaErpTask("protocolo", RegistrarConsultaErpRequest("WF1", "sol1", "12345678000190",
        "98765432000100", "001", BigDecimal.TEN), "atividade", 1, 2, "tabela")

    @Test
    fun `READY manual em ConsultaErp com entrega esgotada reativa o protocolo antes de salvar`() {
        val esgotado = protocolo.copy(status = StatusConsultaErp.RETORNO_FALHOU, ultimoErro = "esgotada")
        val task = Task("protocolo-ConsultaErp", "Pagamento", TaskStatus.FAILED, TaskTypes.ConsultaErp)
            .also { it.consultaErp = ConsultaErpListItem.from(esgotado) }
        `when`(repository.findById(task.id)).thenReturn(Optional.of(task))
        `when`(repository.save(task)).thenReturn(task)
        val reativado = esgotado.copy(status = StatusConsultaErp.RETORNO_PENDENTE)
        `when`(consultaErp.reativarRetorno("protocolo")).thenReturn(reativado)

        val salva = service.updateStatus(task.id, TaskStatus.READY)

        val ordem = org.mockito.Mockito.inOrder(consultaErp, repository)
        ordem.verify(consultaErp).reativarRetorno("protocolo")
        ordem.verify(repository).save(task)
        assertEquals(TaskStatus.READY, salva.status)
        assertEquals(StatusConsultaErp.RETORNO_PENDENTE, salva.consultaErp!!.status)
    }

    @Test
    fun `READY manual em ConsultaErp apenas pausado nao altera o protocolo`() {
        val task = Task("protocolo-ConsultaErp", "Pagamento", TaskStatus.FAILED, TaskTypes.ConsultaErp)
            .also { it.consultaErp = ConsultaErpListItem.from(protocolo) }
        `when`(repository.findById(task.id)).thenReturn(Optional.of(task))
        `when`(repository.save(task)).thenReturn(task)
        `when`(consultaErp.reativarRetorno("protocolo")).thenReturn(null)

        val salva = service.updateStatus(task.id, TaskStatus.READY)

        assertEquals(TaskStatus.READY, salva.status)
        assertEquals(StatusConsultaErp.PENDENTE, salva.consultaErp!!.status)
        service.updateStatus(task.id, TaskStatus.FAILED)
        verify(consultaErp, org.mockito.Mockito.times(1)).reativarRetorno("protocolo")
    }
}
