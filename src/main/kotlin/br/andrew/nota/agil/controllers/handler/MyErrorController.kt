package br.andrew.nota.agil.controllers.handler

import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.webmvc.error.ErrorController
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.NoHandlerFoundException
import java.util.*

class MyErrorController() : ErrorController {

    val log = LoggerFactory.getLogger(MyErrorController::class.java)

    fun getCurrentTrace(): String {
        try{
            return throw Exception("Sem brave")
        }catch (e : Throwable){
            return "SEM TRACE ID"
        }
    }
    @RequestMapping("/error",produces = ["application/json"])
    fun handleError(request: HttpServletRequest, t : Throwable): ResponseEntity<ErroDto> {
        if(t is NoHandlerFoundException){
            if(t.requestURL == "/favicon.ico")
                return ResponseEntity.notFound().build()
        }
        val traceId = getCurrentTrace()
        log.error("Error controller",t)
        val status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)
        val statusCode  = status?.toString()?.toIntOrNull() ?: 500
        val erro = if(statusCode == HttpStatus.FORBIDDEN.value())
            ErroDto("Acesso Negado",traceId)
        else if(statusCode == HttpStatus.NOT_FOUND.value())
            ErroDto(t.message ?: "Pagina Não encontrada",traceId,t)
        else if(t is HttpClientErrorException){
            val msg = t.getResponseBodyAs(String::class.java) ?: "Erro inesperado"
            ErroDto(msg,traceId,t)
        }
        else
            ErroDto(t.message ?: "Erro inesperado",traceId,t)

        return ResponseEntity
            .status(statusCode)
            .header("error", "[$traceId]")
            .header("info", "[$traceId]")
            .header("traceId", traceId)
            .body(erro)
    }

    private fun getTraceParameter(request: HttpServletRequest): Boolean {
        val parameter = request.getParameter("trace") ?: return false
        return "false" != parameter.lowercase(Locale.getDefault())
    }
}

