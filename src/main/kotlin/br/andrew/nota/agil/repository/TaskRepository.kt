package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Order
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface TaskRepository : MongoRepository<Task, String> {

    fun findAllByStatus(taskStatus: TaskStatus, sort: Sort = Sort.by(Order.desc("createdAt"))) : List<Task>

    /** Task de criacao do processo no SE: [recordId] e o id da instancia devolvido pelo SE (task.recordID). */
    fun findAllByTaskTypeAndRecordID(taskType: TaskTypes, recordId: String): List<Task>

    fun findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(
        cnpjPrestador: Collection<String>,
        numeros: Collection<String>
    ): List<Task>

    /**
     * Candidatas a mesma nota: prestador pela regex de [br.andrew.nota.agil.services.NumeroNota.regexDocumentos]
     * (qualquer mascara) + numero pela de [br.andrew.nota.agil.services.NumeroNota.regexCandidatos].
     */
    fun findAllByDuplicataCpfCnpjPrestadorRegexAndDuplicataNumeroRegex(
        prestadorRegex: String,
        numeroRegex: String,
    ): List<Task>
}
