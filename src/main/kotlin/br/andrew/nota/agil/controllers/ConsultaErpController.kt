package br.andrew.nota.agil.controllers

import br.andrew.nota.agil.model.erp.ConsultaErpResponse
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.model.erp.ConsultaErpListItem
import br.andrew.nota.agil.model.erp.StatusConsultaErp
import br.andrew.nota.agil.services.ConsultaErpService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@RestController
@RequestMapping("/integracoes/erp/consultas")
class ConsultaErpController(private val service: ConsultaErpService) {
    @GetMapping
    fun listar(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: StatusConsultaErp?,
        @RequestParam(required = false) workflowId: String?,
    ): Page<ConsultaErpListItem> = service.listar(page, size, status, workflowId).map(ConsultaErpListItem::from)

    @PostMapping
    fun registrar(@Valid @RequestBody request: RegistrarConsultaErpRequest): ResponseEntity<ConsultaErpResponse> {
        val task = service.registrar(request)
        return ResponseEntity.accepted().location(URI.create("/integracoes/erp/consultas/${task.id}"))
            .body(ConsultaErpResponse.from(task))
    }

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: String): ConsultaErpResponse = ConsultaErpResponse.from(service.buscar(id))

    @ExceptionHandler(ResponseStatusException::class)
    fun erroStatus(e: ResponseStatusException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(e.statusCode).body(mapOf("mensagem" to (e.reason ?: "Erro na consulta ERP")))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun erroValidacao(e: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("mensagem" to e.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun erroJson(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("mensagem" to "JSON invalido ou campos obrigatorios ausentes"))
}
