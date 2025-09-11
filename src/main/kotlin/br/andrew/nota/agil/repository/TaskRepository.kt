package br.andrew.nota.agil.repository

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.Task
import br.andrew.nota.agil.model.TaskStatus
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Order
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface TaskRepository : MongoRepository<Task, String> {

    fun findAllByStatus(taskStatus: TaskStatus, sort: Sort = Sort.by(Order.desc("createdAt"))) : List<Task>

}