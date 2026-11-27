package br.andrew.nota.agil.softexpert.infrastructure.interfaces

import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

/** Consultas XML explicitas: o retorno real de getUserData diverge do WSDL publicado. */
@HttpExchange(contentType = "text/xml; charset=UTF-8", accept = ["text/xml"])
interface SoftExpertConsultaSoap {
    @PostExchange("/apigateway/se/ws/adm_ws.php")
    fun usuario(
        @RequestBody envelope: String,
        @RequestHeader("SOAPAction") action: String = "urn:admin#getUserData",
    ): String

    @PostExchange("/apigateway/se/ws/wf_ws.php")
    fun instancia(
        @RequestBody envelope: String,
        @RequestHeader("SOAPAction") action: String = "urn:workflow#getWorkflow",
    ): String
}
