package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.tasks.Task
import br.andrew.nota.agil.model.tasks.TaskStatus
import br.andrew.nota.agil.model.tasks.TaskTypes
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.TaskRepository
import br.andrew.nota.agil.services.TaskService
import br.andrew.nota.agil.softexpert.service.DocumentService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.regex.Pattern

@RestController()
@RequestMapping("/tasks")
class TasksController(
    val repository : TaskRepository,
    val service : TaskService,
    val qiveApi : QiveApiClient,
    val mongoTemplate: MongoTemplate,

) {

    @GetMapping
    fun get(
        page: Pageable,
        @RequestParam(required = false) taskType: TaskTypes?,
        @RequestParam(required = false) status: TaskStatus?,
        @RequestParam(required = false) tomador: String?,
    ): Page<Task> {
        val sort = Sort.by(Sort.Order.desc("createdAt"))
        val enforced = PageRequest.of(page.pageNumber, page.pageSize, sort)
        val query = Query()

        taskType?.let { query.addCriteria(Criteria.where("taskType").`is`(it)) }
        status?.let { query.addCriteria(Criteria.where("status").`is`(it)) }
        tomador?.trim()?.takeIf { it.isNotBlank() }?.let {
            query.addCriteria(
                Criteria.where("duplicata.razaoTomador").regex(Pattern.quote(it), "i"),
            )
        }

        val total = mongoTemplate.count(
            Query.of(query).limit(-1).skip(-1),
            Task::class.java,
        )
        val data = mongoTemplate.find(
            query.with(enforced),
            Task::class.java,
        )

        return PageImpl(data, enforced, total)
    }

    @GetMapping("/{id}")
    fun execute(@PathVariable id : String) {
        val task = repository.findById(id).orElseThrow { Exception("Task $id nao encontrada") }
        return service.executa(task)
    }
}
