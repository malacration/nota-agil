package br.andrew.nota.agil.qive.interfaces.controllers

import br.andrew.nota.agil.qive.interfaces.DocumentPdfResponse
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.model.NotaServico
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.Date


@HttpExchange("/nfse")
interface Nfse {

    @GetExchange("/received")
    fun listReceived(
        @RequestParam("cnpj[]") cnpj : List<String>,
        @RequestParam("cursor") cursor: Int? = null,
        @RequestParam("created_at[from]") from: Date? = null,
        @RequestParam("created_at[to]") to: Date? = null,
        @RequestParam("type") type: List<String>? = null,
        @RequestParam("access_key") accessKey: String? = null,
        @RequestParam("format_type") format : String = "JSON"
    ): ReceivedResponse<NotaServico>

    @GetExchange("/events")
    fun events(
        @RequestParam("access_key") accessKey: String? = null,
    ): ReceivedResponse<Any>

    @GetExchange("/danfse")
    fun danfse(
        @RequestParam("id") id: String,
    ): DocumentPdfResponse
}
