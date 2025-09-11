package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.Task
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.services.TaskService
import br.andrew.wsdl.workflow.NewWorkflowEditDataResponseType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Order
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController()
@RequestMapping("/tasks")
class TasksController(
    val repository : TaskRepository,
    val service : TaskService,
    val qiveApi : QiveApiClient
) {

    @GetMapping
    fun get(page: Pageable): Page<Task> {
        val sort = Sort.by(Sort.Order.desc("createdAt"))
        val enforced = PageRequest.of(page.pageNumber, page.pageSize, sort)
        return repository.findAll(enforced)

    }

    @GetMapping("/{id}")
    fun execute(@PathVariable id : String) {
        val task = repository.findById(id).orElseThrow { Exception("Task $id nao encontrada") }
        return service.executa(task)
    }
}