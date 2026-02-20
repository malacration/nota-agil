package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Order
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface TaskRepository : MongoRepository<Task, String> {

    fun findAllByStatus(taskStatus: TaskStatus, sort: Sort = Sort.by(Order.desc("createdAt"))) : List<Task>

    fun findAllByDuplicataCpfCnpjPrestadorInAndDuplicataNumeroIn(
        cnpjPrestador: Collection<String>,
        numeros: Collection<String>
    ): List<Task>
}
