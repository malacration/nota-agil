package br.andrew.nota.agil.qive.interfaces.controllers

import br.andrew.nota.agil.qive.interfaces.DocumentPdfResponse
import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import br.andrew.nota.agil.qive.model.ConhecimentoTransporte
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.Date

@HttpExchange("/cte")
interface Cte {

    @GetExchange("/taker")
    fun listReceived(
        @RequestParam("cnpj[]") cnpj: List<String>,
        @RequestParam("cursor") cursor: Int? = null,
        @RequestParam("created_at[from]") from: Date? = null,
        @RequestParam("created_at[to]") to: Date? = null,
        @RequestParam("access_key") accessKey: String? = null,
        @RequestParam("state[]") state: List<String>? = null,
        @RequestParam("authorization_role[]") authorizationRole: List<String>? = null,
        @RequestParam("format_type") format: String = "JSON",
    ): ReceivedResponse<ConhecimentoTransporte>

    @GetExchange("/dacte")
    fun dacte(
        @RequestParam("access_key") accessKey: String
    ): DocumentPdfResponse
}
