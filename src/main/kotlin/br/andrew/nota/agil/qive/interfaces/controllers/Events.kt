package br.andrew.nota.agil.qive.interfaces.controllers

import br.andrew.nota.agil.qive.interfaces.ReceivedResponse
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import java.util.Date


@HttpExchange("/events")
interface Events {

    @GetExchange("/nfe")
    fun listReceived(
        @RequestParam("type") type: List<String>? = null,
        @RequestParam("cursor") cursor: Int? = null,
        @RequestParam("access_key") accessKey: String? = null,
        @RequestParam("format_type") format : String = "JSON"
    ): ReceivedResponse<Any>
}
