package br.andrew.nota.agil.controllers.handler

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class ErroDto(val mensagem : String, val traceId : String) {

    var stackTrace : String? = null
    var bodyResposta : String? = null
    var endpoint : String? = null
    var parametros : Map<String, Any?>? = null
    constructor(mensagem: String, traceId: String, t: Throwable) : this(mensagem, traceId){
        this.stackTrace = t.stackTraceToString()
    }
}
