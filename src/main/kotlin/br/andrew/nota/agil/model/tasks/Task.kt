package br.andrew.nota.agil.model.tasks

import br.andrew.nota.agil.model.Duplicata
import br.andrew.nota.agil.model.erp.ConsultaErpListItem
import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.Date


@Document("task")
class Task(
    @Id val id : String,
    val name : String,
    var status : TaskStatus,
    val taskType : TaskTypes,
    val duplicata : Duplicata? = null,
    val lastRunAt : Date? = null) {

    var recordID: String? = null
    var recordKey: String? = null
    var consultaErp: ConsultaErpListItem? = null
    var tomador: String? = null

    /** Fonte da nota: null nas tasks antigas e nas do Qive; SAP/DEALER nas importadas de um ERP. */
    var origem: OrigemNota? = null
    /** So tasks importadas de ERP: referencia da nota no ERP (ex.: para baixar o anexo do SAP). */
    var notaErp: ReferenciaNotaErp? = null
    /** So UploadPdfErp concluida: de onde veio o PDF anexado (QIVE, SAP ou NENHUM). */
    var origemAnexo: String? = null
    /**
     * So CreateTask criada a partir desta versao: identidade fiscal canonica da nota (prestador +
     * tomador + tipo + numero + serie), com indice unico esparso (ver IndiceIdentidadeNota). Duas
     * tasks da mesma nota nao coexistem, qualquer que seja a fonte ou o tempo de cada criacao.
     * Null nas tasks antigas (fora do indice).
     */
    var identidadeNota: String? = null

    @field:CreatedDate
    var createdAt: Instant = Instant.now()

    @JsonIgnore
    fun getWorkFlowId(): String? {
        return recordID
    }

    fun changeStatus(newStatus: TaskStatus) {
        if (status == newStatus) {
            return
        }

        val allowedTransitions = when (status) {
            TaskStatus.READY -> setOf(TaskStatus.FAILED)
            TaskStatus.FAILED -> setOf(TaskStatus.READY)
            TaskStatus.FINISHED -> emptySet()
        }

        if (newStatus !in allowedTransitions) {
            throw IllegalStateException(
                "Nao e permitido alterar status manualmente de $status para $newStatus. " +
                    "Transicoes permitidas: READY <-> FAILED.",
            )
        }

        status = newStatus
    }

    init{
        if(taskType == TaskTypes.CreateTask && duplicata == null)
            throw Exception("Nao é permitido tarefa de criar com duplicata null")
    }
}



//TODO rever esses status
enum class TaskStatus {
    READY,
    FAILED,
    FINISHED,
}

enum class TaskTypes {
    CreateTask,
    UploadPdf,
    CancelTask,
    ConsultaErp,
    /**
     * Anexo de nota importada de ERP: tenta o Qive (pela chave ou buscando a nota recebida); SAP
     * tenta ainda o anexo da nota no SAP; sem PDF, segue sem anexo.
     */
    UploadPdfErp,
}

enum class OrigemNota {
    QIVE,
    SAP,
    DEALER,
}

/**
 * Nota no ERP de origem. [id]: SAP = OPCH.DocEntry; Dealer = chave da nota agrupada (ver
 * DealerNotasEntradaClient). [cnpjFilial] e conferido pelo ERP no download do anexo.
 */
data class ReferenciaNotaErp(
    val erp: br.andrew.nota.agil.model.erp.Erp,
    val id: String,
    val cnpjFilial: String,
    val anexosPdf: Int = 0,
)
