package br.andrew.nota.agil.controllers.handler

import br.andrew.nota.agil.controllers.SoftExpertUsuarioController
import br.andrew.nota.agil.controllers.WorkflowController
import br.andrew.nota.agil.softexpert.infrastructure.ApiException
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftExpertException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.client.RestClientException

@RestControllerAdvice(assignableTypes = [WorkflowController::class, SoftExpertUsuarioController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class SoftExpertConsultaExceptionHandler {
    @ExceptionHandler(ResponseStatusException::class)
    fun status(e: ResponseStatusException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(e.statusCode).body(mapOf("mensagem" to (e.reason ?: "Erro na consulta ao SoftExpert")))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun parametro(e: MethodArgumentTypeMismatchException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("mensagem" to "Parametro ${e.name} invalido"))

    @ExceptionHandler(ApiException::class, SoftExpertException::class, RestClientException::class)
    fun upstream(e: RuntimeException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(mapOf("mensagem" to "SoftExpert recusou a consulta ou retornou uma resposta sem sucesso"))
}
