package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.sap.SapNotasEntradaClient
import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.tasks.OrigemNota
import br.andrew.nota.agil.model.tasks.ReferenciaNotaErp
import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.qive.interfaces.DocumentPdfData
import br.andrew.nota.agil.qive.interfaces.DocumentPdfResponse
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
import br.andrew.wsdl.document.NewDocument2RequestType
import br.andrew.wsdl.document.NewDocument2ResponseType
import br.andrew.wsdl.workflow.ExecuteActivityRequestType
import br.andrew.wsdl.workflow.ExecuteActivityResponseType
import br.andrew.wsdl.workflow.NewAssocDocumentRequestType
import br.andrew.wsdl.workflow.NewAssocDocumentResponseType
import br.andrew.wsdl.workflow.NewWorkflowEditDataRequestType
import br.andrew.wsdl.workflow.NewWorkflowEditDataResponseType
import br.andrew.wsdl.workflow.WorkflowPortType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.util.Base64
import java.util.Date

/** Anexo de nota importada de ERP: Qive -> (SAP: anexo do SAP) -> sem anexo. Dados ficticios. */
class TaskServiceUploadErpTest {

    private val repository = mock(TaskRepository::class.java)
    private val workflow = mock(WorkflowPortType::class.java)
    private val documentos = mock(DocumentoPortType::class.java)
    private val nfe = mock(Nfe::class.java)
    private val nfse = mock(Nfse::class.java)
    private val sap = mock(SapNotasEntradaClient::class.java)
    private val qiveApi = QiveApiClient(nfe, mock(Events::class.java), mock(Company::class.java), nfse, mock(Cte::class.java))
    private val service = TaskService(
        flow = WorkFlowEnvrioment("process", "table", "doc", "atividadeUpload"),
        workFlowService = WorkFlowService(workflow, DocumentService(documentos)),
        taskRepository = repository,
        documentoService = DocumentService(documentos),
        qiveApi = qiveApi,
        consultaErpService = mock(ConsultaErpService::class.java),
        sapNotas = sap,
        qivePdf = QivePdfLocator(qiveApi),
    )

    private val chave = "11261198765432000100550010000001231000000001"
    private val pdf = "%PDF-1.4\n%fake\n".toByteArray()

    private fun upload(tipo: TipoDuplicata = TipoDuplicata.Nfe, chaveAcesso: String? = chave, erp: Erp = Erp.SAP) =
        Task("base-UploadPdfErp", "Titulo", TaskStatus.READY, TaskTypes.UploadPdfErp,
            Duplicata(Date(), "98765432000100", "F", "12345678000190", "E", BigDecimal.ONE, BigDecimal.ONE, "123", tipo,
                chaveAcesso = chaveAcesso)).also {
            it.recordID = "WF1"
            it.recordKey = "K1"
            it.origem = if (erp == Erp.SAP) OrigemNota.SAP else OrigemNota.DEALER
            it.notaErp = ReferenciaNotaErp(erp, if (erp == Erp.SAP) "55" else "nf9", "12345678000190", 1)
        }

    private fun seAceita() {
        `when`(documentos.newDocument2(any(NewDocument2RequestType::class.java)))
            .thenReturn(NewDocument2ResponseType().also { it.status = "SUCCESS"; it.recordID = "DOC1" })
        `when`(workflow.newAssocDocument(any(NewAssocDocumentRequestType::class.java)))
            .thenReturn(NewAssocDocumentResponseType().also { it.status = "SUCCESS" })
        `when`(workflow.executeActivity(any(ExecuteActivityRequestType::class.java)))
            .thenReturn(ExecuteActivityResponseType().also { it.status = "SUCCESS" })
    }

    private fun qivePdf() = DocumentPdfResponse(DocumentPdfData(chave, Base64.getEncoder().encodeToString(pdf)))

    @Test
    fun `usa o PDF do Qive quando existe`() {
        seAceita()
        `when`(nfe.danfe(chave)).thenReturn(qivePdf())
        val task = upload()

        service.executa(task)

        assertEquals(TaskStatus.FINISHED, task.status)
        assertEquals("QIVE", task.origemAnexo)
        verify(workflow).newAssocDocument(any(NewAssocDocumentRequestType::class.java))
        verify(sap, never()).anexoPdf(55, "12345678000190")
    }

    @Test
    fun `Qive falhou usa o anexo do SAP`() {
        seAceita()
        `when`(nfe.danfe(chave)).thenThrow(RuntimeException("404 Qive"))
        `when`(sap.anexoPdf(55, "12345678000190")).thenReturn(pdf)
        val task = upload()

        service.executa(task)

        assertEquals("SAP", task.origemAnexo)
        assertEquals(TaskStatus.FINISHED, task.status)
        val doc = ArgumentCaptor.forClass(NewDocument2RequestType::class.java)
        verify(documentos).newDocument2(doc.capture())
        assertEquals(pdf.toList(), doc.value.files[0].content.toList())
    }

    @Test
    fun `NFS-e sem id do Qive vai direto ao anexo do SAP`() {
        seAceita()
        `when`(sap.anexoPdf(55, "12345678000190")).thenReturn(pdf)
        val task = upload(TipoDuplicata.Nfse, chaveAcesso = null)

        service.executa(task)

        assertEquals("SAP", task.origemAnexo)
        verify(nfse, never()).danfse(chave)
    }

    @Test
    fun `sem PDF em nenhuma fonte segue sem anexo e executa a atividade`() {
        seAceita()
        `when`(nfe.danfe(chave)).thenThrow(RuntimeException("404 Qive"))
        `when`(sap.anexoPdf(55, "12345678000190")).thenThrow(RuntimeException("502 sap-rovema"))
        val task = upload()

        service.executa(task)

        assertEquals("NENHUM", task.origemAnexo)
        assertEquals(TaskStatus.FINISHED, task.status)
        verify(documentos, never()).newDocument2(any(NewDocument2RequestType::class.java))
        verify(workflow, never()).newAssocDocument(any(NewAssocDocumentRequestType::class.java))
        verify(workflow).executeActivity(any(ExecuteActivityRequestType::class.java))
    }

    @Test
    fun `Dealer sem PDF no Qive segue sem anexo e nunca consulta o SAP`() {
        seAceita()
        val task = upload(TipoDuplicata.NaoDetectado, chaveAcesso = null, erp = Erp.DEALER)

        service.executa(task)

        assertEquals("NENHUM", task.origemAnexo)
        assertEquals(TaskStatus.FINISHED, task.status)
        verify(sap, never()).anexoPdf(55, "12345678000190")
        verify(documentos, never()).newDocument2(any(NewDocument2RequestType::class.java))
    }

    @Test
    fun `SE recusando a atividade marca FAILED`() {
        `when`(sap.anexoPdf(55, "12345678000190")).thenReturn(null)
        `when`(workflow.executeActivity(any(ExecuteActivityRequestType::class.java)))
            .thenReturn(ExecuteActivityResponseType().also { it.status = "FAILURE"; it.detail = "atividade nao habilitada" })
        val task = upload(TipoDuplicata.Nfse, chaveAcesso = null)

        assertThrows<Exception> { service.executa(task) }
        assertEquals(TaskStatus.FAILED, task.status)
    }

    @Test
    fun `criacao do processo de nota de ERP gera UploadPdfErp`() {
        `when`(workflow.newWorkflowEditData(any(NewWorkflowEditDataRequestType::class.java)))
            .thenReturn(NewWorkflowEditDataResponseType().also { it.status = "SUCCESS"; it.recordID = "WF9"; it.recordKey = "K9" })
        val duplicata = Duplicata(Date(), "98765432000100", "F", "12345678000190", "E", BigDecimal.ONE, BigDecimal.ONE,
            "123", TipoDuplicata.Nfse)
        val sapTask = Task("sap-55-CreateTask", "Titulo", TaskStatus.READY, TaskTypes.CreateTask, duplicata).also {
            it.origem = OrigemNota.SAP
            it.notaErp = ReferenciaNotaErp(Erp.SAP, "55", "12345678000190", 1)
        }

        service.executa(sapTask)

        @Suppress("UNCHECKED_CAST")
        val salvas = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<Task>>
        verify(repository).saveAll(salvas.capture())
        val upload = salvas.value.toList()[1]
        assertEquals("sap-55-UploadPdfErp", upload.id)
        assertEquals(TaskTypes.UploadPdfErp, upload.taskType)
        assertEquals("WF9", upload.recordID)
        assertEquals(OrigemNota.SAP, upload.origem)
        assertEquals(ReferenciaNotaErp(Erp.SAP, "55", "12345678000190", 1), upload.notaErp)
    }
}
